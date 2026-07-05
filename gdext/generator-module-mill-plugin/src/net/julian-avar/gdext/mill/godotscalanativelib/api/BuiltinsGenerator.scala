package net.`julian-avar`.gdext.mill
package godotscalanativelib.api

import scala.meta.*
import godotscalanativelib.resource_parser.Ast
import godotscalanativelib.utils.*

private val builtinVariantCodes: Map[String, Int] = Map(
  "Vector2"     -> 5,
  "Vector2i"    -> 6,
  "Rect2"       -> 7,
  "Rect2i"      -> 8,
  "Vector3"     -> 9,
  "Vector3i"    -> 10,
  "Transform2D" -> 11,
  "Vector4"     -> 12,
  "Vector4i"    -> 13,
  "Plane"       -> 14,
  "Quaternion"  -> 15,
  "AABB"        -> 16,
  "Basis"       -> 17,
  "Transform3D" -> 18,
  "Projection"  -> 19,
  "Color"       -> 20
)

class BuiltinsGenerator(using dialect: Dialect):
    private def variantTypeCode(name: String): Option[Int] = builtinVariantCodes.get(name)
    def generate(
        builtins: Vector[Ast.BuiltinClass],
        folder: String,
        file: String
    ): Vector[ScalaFile] =
        val sorted                  = topoSort(builtins)
        val (valueTypes, heapTypes) = sorted.partition(_.members.nonEmpty)
        Vector(
          ScalaFile(content = builtinsSource(valueTypes, heapTypes), path = folder, name = file)
        )
    end generate

    private def builtinsSource(
        valueTypes: Vector[Ast.BuiltinClass],
        heapTypes: Vector[Ast.BuiltinClass]
    ): String =
        // Name of the private layout struct type for a given builtin
        def privateStructName(name: String): String = s"_${name}Struct"

        // CStruct type using PRIVATE struct names for non-primitive member types
        def cstructTypeName(members: Vector[Ast.BuiltinMember]): Type =
            val args = members.map { m =>
                val t = metaToScalaType(m.meta)
                if isPrimitiveMeta(m.meta) then Type.Name(t) else Type.Name(privateStructName(t))
            }.toList
            Type.Apply(Type.Name(s"CStruct${members.length}"), args)
        end cstructTypeName

        def buildApplyCtor(name: String, members: Vector[Ast.BuiltinMember]): Option[Defn.Def] =
            if !members.forall(m => isPrimitiveMeta(m.meta)) then None
            else
                val privateName = privateStructName(name)
                val params      = members.map { m =>
                    Term.Param(
                      Nil,
                      Term.Name(m.name),
                      Some(Type.Name(metaToScalaType(m.meta))),
                      None
                    )
                }.toList
                val sets = members.zipWithIndex.map { (m, i) =>
                    Term.Assign(
                      Term.Select(Term.Name("p"), Term.Name(s"_${i + 1}")),
                      Term.Name(m.name)
                    )
                }.toList
                val body = Term.Block(
                  (simpleValDef("p", stackallocTerm(Type.Name(privateName))) :: sets) :+
                      Term.Name("p")
                )
                Some(buildInlineDef("apply", List(params), Type.Name(name), body))

        def buildFieldExtMethods(name: String, members: Vector[Ast.BuiltinMember]): List[Defn.Def] =
            members.zipWithIndex.flatMap { (m, i) =>
                val idx = i + 1
                val fn  = m.name
                val tp  = metaToScalaType(m.meta)
                if isPrimitiveMeta(m.meta) then
                    val getter = buildInlineDef(
                      fn,
                      Nil,
                      Type.Name(tp),
                      Term.Select(Term.Name("v"), Term.Name(s"_$idx"))
                    )
                    val setter = buildInlineDef(
                      s"${fn}_=",
                      List(List(Term.Param(Nil, Term.Name("value"), Some(Type.Name(tp)), None))),
                      Type.Name("Unit"),
                      Term.Assign(
                        Term.Select(Term.Name("v"), Term.Name(s"_$idx")),
                        Term.Name("value")
                      )
                    )
                    List(getter, setter)
                else
                    // Non-primitive: at1/at2/... returns Ptr[_StructType] = PublicType inside companion
                    List(buildInlineDef(
                      fn,
                      Nil,
                      Type.Name(tp),
                      Term.Select(Term.Name("v"), Term.Name(s"at$idx"))
                    ))
                end if
            }.toList

        def buildMathExtMethods(name: String, members: Vector[Ast.BuiltinMember]): List[Defn.Def] =
            val metas = members.map(_.meta).distinct
            if !members.forall(m => isPrimitiveMeta(m.meta)) || metas.length != 1 then
                return List.empty
            val tp          = metaToScalaType(metas.head)
            val fields      = members.map(m => m.name).toList
            val selfType    = Type.Name(name)
            val privateName = Type.Name(privateStructName(name))
            val isIntegral  = tp == "Int" || tp == "Long"

            val unaryFnType  = Type.Function(List(Type.Name(tp)), Type.Name(tp))
            val binaryFnType = Type.Function(List(Type.Name(tp), Type.Name(tp)), Type.Name(tp))

            def mapBody: Term =
                val sets = fields.map { f =>
                    Term.Assign(
                      Term.Select(Term.Name("result"), Term.Name(f)),
                      Term.Apply(Term.Name("f"), List(Term.Select(Term.Name("v"), Term.Name(f))))
                    )
                }
                Term.Block(
                  (simpleValDef("result", stackallocTerm(privateName)) :: sets) :+
                      Term.Name("result")
                )
            end mapBody

            def combineBody: Term =
                val sets = fields.map { f =>
                    Term.Assign(
                      Term.Select(Term.Name("result"), Term.Name(f)),
                      Term.Apply(
                        Term.Name("f"),
                        List(
                          Term.Select(Term.Name("v"), Term.Name(f)),
                          Term.Select(Term.Name("o"), Term.Name(f))
                        )
                      )
                    )
                }
                Term.Block(
                  (simpleValDef("result", stackallocTerm(privateName)) :: sets) :+
                      Term.Name("result")
                )
            end combineBody

            val mapDef = buildInlineDef(
              "map",
              List(List(Term.Param(Nil, Term.Name("f"), Some(unaryFnType), None))),
              selfType,
              mapBody
            )
            val combineDef = buildInlineDef(
              "combine",
              List(
                List(Term.Param(Nil, Term.Name("o"), Some(selfType), None)),
                List(Term.Param(Nil, Term.Name("f"), Some(binaryFnType), None))
              ),
              selfType,
              combineBody
            )

            def scalarOp(op: String): Defn.Def = buildInlineDef(
              op,
              List(List(Term.Param(Nil, Term.Name("scalar"), Some(Type.Name(tp)), None))),
              selfType,
              s"v.map(_ $op scalar)".parse[Term].get
            )
            def vectorOp(op: String): Defn.Def = buildInlineDef(
              op,
              List(List(Term.Param(Nil, Term.Name("o"), Some(selfType), None))),
              selfType,
              s"v.combine(o)(_ $op _)".parse[Term].get
            )

            val alwaysOps = List(scalarOp("*"), vectorOp("*"), scalarOp("/"), vectorOp("/"))
            val addSubOps =
                if isIntegral then List(vectorOp("+"), vectorOp("-"))
                else List(scalarOp("+"), scalarOp("-"), vectorOp("+"), vectorOp("-"))

            List(mapDef, combineDef) ++ alwaysOps ++ addSubOps
        end buildMathExtMethods

        // ── Private layout struct type aliases ─────────────────────────────────
        // e.g. `private type _Vector2Struct = CStruct2[Float, Float]`
        val privateTypeDefs: List[Stat] = valueTypes.map { b =>
            val privateName = privateStructName(b.name)
            s"private type $privateName = ${cstructTypeName(b.members).syntax}".parse[Stat].get
        }.toList

        val valueTypeNames: Set[String] = valueTypes.map(_.name).toSet

        def buildConstants(b: Ast.BuiltinClass): List[Defn.Def] =
            val memberCount = b.members.length
            b.constants.flatMap { c =>
                val exprStr = c.value.replace("inf", "Float.PositiveInfinity")
                // Skip constants whose value contains a constructor call whose arg count
                // doesn't match the member count — those use raw floats for compound types
                // (e.g. Basis(1,0,0, 0,1,0, 0,0,1) has 9 args but Basis has 3 members).
                val openParen  = exprStr.indexOf('(')
                val closeParen = exprStr.lastIndexOf(')')
                if openParen != -1 && closeParen > openParen then
                    val argsStr  = exprStr.substring(openParen + 1, closeParen)
                    val argCount = argsStr.split(',').length
                    if argCount != memberCount then None
                    else
                        try
                            val valueTerm = exprStr.parse[Term].get
                            Some(buildInlineDef(c.name, Nil, Type.Name(b.name), valueTerm))
                        catch case _ => None
                    end if
                else
                    try
                        val valueTerm = exprStr.parse[Term].get
                        Some(buildInlineDef(c.name, Nil, Type.Name(b.name), valueTerm))
                    catch case _ => None
                end if
            }.toList
        end buildConstants

        val valueStats: List[Stat] = valueTypes.flatMap { b =>
            val privateName = privateStructName(b.name)
            val privateType = Type.Name(privateName)
            val ptrPrivate  = Type.Apply(Type.Name("Ptr"), List(privateType))
            val selfType    = Type.Name(b.name)
            val extParam    = Term.Param(Nil, Term.Name("v"), Some(selfType), None)

            val opaqueType = Defn.Type(
              List(Mod.Opaque()),
              Type.Name(b.name),
              Nil,
              ptrPrivate,
              Type.Bounds(None, None)
            )

            val byteSize = Defn.Val(
              Nil,
              List(Pat.Var(Term.Name("byteSize"))),
              Some(Type.Name("CSize")),
              Term.ApplyType(Term.Name("sizeof"), List(privateType))
            )
            val allocDef     = buildInlineDef("alloc", Nil, selfType, stackallocTerm(privateType))
            val zoneAllocDef = s"def zoneAlloc()(using Zone): ${b
                    .name} = scala.scalanative.unsafe.alloc[${privateName}]()".parse[Stat].get
            val writeToDef = Defn.Def(
              Nil,
              Term.Name("writeTo"),
              Nil,
              List(List(
                Term.Param(Nil, Term.Name("dest"), Some(ptrByte), None),
                Term.Param(Nil, Term.Name("value"), Some(selfType), None)
              )),
              Some(Type.Name("Unit")),
              Term.Apply(
                Term.Select(Term.Name("Variant"), Term.Name("writeBuiltin")),
                List(Lit.Int(builtinVariantCodes(b.name)), Term.Name("dest"), Term.Name("value"))
              )
            )
            val readFromDef = Defn.Def(
              Nil,
              Term.Name("readFrom"),
              Nil,
              List(List(Term.Param(Nil, Term.Name("src"), Some(ptrByte), None))),
              Some(selfType),
              Term.Apply(
                Term.ApplyType(
                  Term.Select(Term.Name("Variant"), Term.Name("readBuiltin")),
                  List(privateType)
                ),
                List(Term.Name("src"))
              )
            )

            val extMethods = buildFieldExtMethods(b.name, b.members) ++
                buildMathExtMethods(b.name, b.members)
            val objectStats: List[Stat] = List(byteSize, allocDef, zoneAllocDef) ++
                buildApplyCtor(b.name, b.members).toList ++ buildConstants(b) ++
                List(writeToDef, readFromDef) ++
                List(buildExtensionGroup(extParam, Nil, extMethods))
            List(opaqueType, simpleObject(b.name, objectStats))
        }.toList

        // scalameta only preserves comments on the exact tree passed to `.syntax`; once these defs
        // become members of the enclosing `Pkg`, any comment attached to them individually is
        // discarded on reprint. So comments are collected as (marker, text) pairs here and spliced
        // into the fully-printed file text at the very end via `injectComments`.
        //
        // Constant names (e.g. `ZERO`) repeat across different builtin types sharing this one
        // file, and `injectComments` resolves repeated markers by consuming successive occurrences
        // in list order -- so each type's class marker and its own constants must stay adjacent
        // and in the same order they're emitted in `valueStats` above (class, then its constants).
        val valueMarkers: List[(String, String)] = valueTypes.flatMap { b =>
            val fallback =
                s"Godot value type. Use ${b.name}(...) or ${b.name}.alloc() to create instances."
            val desc        = combineDescriptions(b.briefDescription, b.description)
                .orElse(Some(fallback))
            val classMarker = s"opaque type ${b.name} =" -> formatComment(desc, None)
            val constMarkers = b.constants.flatMap { c =>
                c.description.map(d => s"def ${c.name}:" -> formatComment(Some(d), None))
            }
            classMarker +: constMarkers
        }.toList

        val heapClassMarkers: List[(String, String)] = heapTypes.flatMap { b =>
            combineDescriptions(b.briefDescription, b.description)
                .map(d => s"class ${b.name}(" -> formatComment(Some(d), None))
        }.toList

        val heapStats: List[Stat] = heapTypes
            .map(b => s"class ${b.name}(val ptr: Ptr[Byte])".parse[Stat].get).toList

        val variantInstances: List[Stat] = valueTypes.flatMap { b =>
            variantTypeCode(b.name).map { code =>
                val selfType    = Type.Name(b.name)
                val privateName = Type.Name(privateStructName(b.name))
                val toName      = Term.Name(s"toVariant${b.name}")
                val fromName    = Term.Name(s"fromVariant${b.name}")
                val toVariant   = Defn.GivenAlias(
                  Nil,
                  toName,
                  Nil,
                  Nil,
                  Type.Apply(Type.Name("ToVariant"), List(selfType)),
                  Term.NewAnonymous(Template(
                    Nil,
                    List(Init(
                      Type.Apply(Type.Name("ToVariant"), List(selfType)),
                      Name.Anonymous(),
                      List.empty[List[Term]]
                    )),
                    Self(Name.Anonymous(), None),
                    List(
                      Defn.Def(Nil, Term.Name("variantType"), Nil, Nil, None, Lit.Int(code)),
                      Defn.Def(
                        Nil,
                        Term.Name("write"),
                        Nil,
                        List(List(
                          Term.Param(Nil, Term.Name("dest"), Some(ptrByte), None),
                          Term.Param(Nil, Term.Name("value"), Some(selfType), None)
                        )),
                        Some(Type.Name("Unit")),
                        Term.Apply(
                          Term.Select(Term.Name("Variant"), Term.Name("writeBuiltin")),
                          List(Lit.Int(code), Term.Name("dest"), Term.Name("value"))
                        )
                      )
                    )
                  ))
                )
                val fromVariant = Defn.GivenAlias(
                  Nil,
                  fromName,
                  Nil,
                  Nil,
                  Type.Apply(Type.Name("FromVariant"), List(selfType)),
                  Term.NewAnonymous(Template(
                    Nil,
                    List(Init(
                      Type.Apply(Type.Name("FromVariant"), List(selfType)),
                      Name.Anonymous(),
                      List.empty[List[Term]]
                    )),
                    Self(Name.Anonymous(), None),
                    List(Defn.Def(
                      Nil,
                      Term.Name("read"),
                      Nil,
                      List(List(Term.Param(Nil, Term.Name("src"), Some(ptrByte), None))),
                      None,
                      Term.Apply(
                        Term.ApplyType(
                          Term.Select(Term.Name("Variant"), Term.Name("readBuiltin")),
                          List(privateName)
                        ),
                        List(Term.Name("src"))
                      )
                    ))
                  ))
                )
                val exportTypeName = Term.Name(s"exportType${b.name}")
                val exportType     = Defn.GivenAlias(
                  Nil,
                  exportTypeName,
                  Nil,
                  Nil,
                  Type.Apply(Type.Name("ExportType"), List(selfType)),
                  Term.NewAnonymous(Template(
                    Nil,
                    List(Init(
                      Type.Apply(Type.Name("ExportType"), List(selfType)),
                      Name.Anonymous(),
                      List.empty[List[Term]]
                    )),
                    Self(Name.Anonymous(), None),
                    List(
                      Defn.Def(Nil, Term.Name("variantType"), Nil, Nil, None, Lit.Int(code)),
                      Defn.Def(
                        Nil,
                        Term.Name("write"),
                        Nil,
                        List(List(
                          Term.Param(Nil, Term.Name("dest"), Some(ptrByte), None),
                          Term.Param(Nil, Term.Name("value"), Some(selfType), None)
                        )),
                        Some(Type.Name("Unit")),
                        Term.Apply(
                          Term.Select(Term.Name("Variant"), Term.Name("writeBuiltin")),
                          List(Lit.Int(code), Term.Name("dest"), Term.Name("value"))
                        )
                      ),
                      Defn.Def(
                        Nil,
                        Term.Name("read"),
                        Nil,
                        List(List(Term.Param(Nil, Term.Name("src"), Some(ptrByte), None))),
                        Some(selfType),
                        Term.Apply(
                          Term.ApplyType(
                            Term.Select(Term.Name("Variant"), Term.Name("readBuiltin")),
                            List(privateName)
                          ),
                          List(Term.Name("src"))
                        )
                      )
                    )
                  ))
                )
                List(toVariant, fromVariant, exportType)
            }.getOrElse(Nil)
        }.toList

        val printed = buildSource(
          "net.`julian-avar`.gdext.generated",
          List(
            "import scala.scalanative.unsafe.{*, given}",
            "import scala.scalanative.unsigned.*",
            "import net.`julian-avar`.gdext.core.{ToVariant, FromVariant, ExportType, Variant}"
          ),
          privateTypeDefs ++ valueStats ++ heapStats ++ variantInstances
        )
        val markers = valueMarkers ++ heapClassMarkers
        "// Generated by gdext generator — do not edit.\n" + injectComments(printed, markers)
    end builtinsSource
end BuiltinsGenerator
