package net.`julian-avar`.gdext.mill
package godotscalanativelib.api

import scala.meta.*
import godotscalanativelib.resource_parser.Ast
import godotscalanativelib.utils.*

class WrappersGenerator(using dialect: Dialect):
    def generate(
        classes: Vector[Ast.GodotClass],
        valueBuiltins: Set[String],
        refcountedTypes: Set[String],
        folder: String
    ): Vector[ScalaFile] =
        val byName = classes.map(c => c.name -> c).toMap
        classes.map { cls =>
            ScalaFile(
              content = wrapperSource(cls, valueBuiltins, refcountedTypes, byName),
              path = folder,
              name = cls.name
            )
        }
    end generate

    private def wrapperSource(
        cls: Ast.GodotClass,
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty,
        byName: Map[String, Ast.GodotClass] = Map.empty
    ): String =
        val instanceMethods = cls.methods.filter { m =>
            !m.isVirtual && !m.isStatic && !jvmMethodConflicts.contains(toCamel(m.name))
        }
        val staticMethods = cls.methods.filter { m =>
            !m.isVirtual && m.isStatic && !jvmMethodConflicts.contains(toCamel(m.name))
        }
        val virtuals = cls.methods.filter(_.isVirtual)

        val inheritedVirtualNames = allInheritedVirtualScalaNames(cls, byName)
        val methodDefs            = instanceMethods.map(buildMethod(
          cls,
          _,
          valueBuiltins = valueBuiltins,
          refcountedTypes = refcountedTypes,
          inheritedVirtualNames = inheritedVirtualNames
        ))
        val staticDefs = staticMethods.map(buildMethod(
          cls,
          _,
          isStatic = true,
          valueBuiltins = valueBuiltins,
          refcountedTypes = refcountedTypes
        ))
        val siblingNames = allVisibleNonVirtualNames(cls, byName)
        val virtualPairs = virtuals
            .flatMap(m => buildVirtualStub(m, valueBuiltins, siblingNames).map(m -> _))
        val virtualDefs  = virtualPairs.map(_._2)

        // scalameta only preserves comments on the exact tree passed to `.syntax`; once these defs
        // become members of the enclosing class/companion `Template`, any comment attached to them
        // individually is discarded on reprint. So comments are collected as (marker, text) pairs
        // here and spliced into the fully-printed file text at the very end via `injectComments`.
        val methodMarkers: List[(String, String)] =
            (instanceMethods.zip(methodDefs) ++ staticMethods.zip(staticDefs) ++ virtualPairs)
                .flatMap((m, stat) => defMarker(stat).map(_ -> formatComment(m.description, None)))
                .toList

        val propMarkers: List[(String, String)] = cls.properties.flatMap { p =>
            val hasGetter = cls.methods.exists { m =>
                m.name == p.getter && !m.isVirtual && !m.isStatic && m.args.forall(_.hasDefault)
            }
            Option.when(hasGetter)(s"def ${toCamel(p.name)}:" -> formatComment(p.description, None))
        }.toList

        val propStats: List[Stat] = cls.properties.flatMap { p =>
            val field         = toCamel(p.name)
            val getterStatOpt = cls.methods.find { m =>
                m.name == p.getter && !m.isVirtual && !m.isStatic && m.args.forall(_.hasDefault)
            }.map { gm =>
                val retType = godotType(gm.returnTypeName, gm.returnMeta, valueBuiltins)
                val getterDef: Defn.Def = if valueBuiltins.contains(gm.returnTypeName) then
                    // Value-builtin getter: the named method requires `(using Zone)` which a
                    // no-arg property accessor can't supply. Build the body with malloc instead
                    // (leaks `T.byteSize` per call — acceptable for occasional property reads).
                    // Use `getXxx()(using zone)` for tight-loop, zero-leak access.
                    val (retAlloc, retName) = stackallocVBRetAlloc(gm.returnTypeName)
                    val argsNull = simpleValDef("_args", asInstanceOfTerm(Lit.Null(), ptrPtrByte))
                    val callStat: Stat = Term.Apply(
                      Term.Select(Term.Name("GdxApi"), Term.Name("ptrcall")),
                      List(
                        Term.Select(
                          Term.Select(Term.Name(cls.name), Term.Name("Binds")),
                          Term.Name(toCamel(p.getter))
                        ),
                        Term.Name("ptr"),
                        Term.Name("_args"),
                        asInstanceOfTerm(retName, ptrByte)
                      )
                    )
                    Defn.Def(
                      Nil,
                      Term.Name(field),
                      Nil,
                      Nil,
                      Some(retType),
                      Term.Block(List(retAlloc, argsNull, callStat, retName))
                    )
                else
                    Defn.Def(
                      Nil,
                      Term.Name(field),
                      Nil,
                      Nil,
                      Some(retType),
                      Term.Apply(Term.Name(toCamel(p.getter)), Nil)
                    )
                getterDef
            }
            val setterStatOpt = p.setter.flatMap { sName =>
                cls.methods.find { m =>
                    m.name == sName && !m.isVirtual && !m.isStatic &&
                    m.args.count(!_.hasDefault) == 1
                }.map { sm =>
                    val spt =
                        paramGodotType(sm.args.head.typeName, sm.args.head.meta, valueBuiltins)
                    Defn.Def(
                      Nil,
                      Term.Name(s"${field}_="),
                      Nil,
                      List(List(Term.Param(Nil, Term.Name("v"), Some(spt), None))),
                      Some(Type.Name("Unit")),
                      Term.Apply(Term.Name(toCamel(sName)), List(Term.Name("v")))
                    )
                }
            }
            getterStatOpt.toList ++ setterStatOpt.toList
        }.toList

        val allMethods             = instanceMethods ++ staticMethods
        val bindsStats: List[Stat] = allMethods.map { m =>
            lazyValDef(
              toCamel(m.name),
              ptrByte,
              Term.Apply(
                Term.Select(Term.Name("GdxApi"), Term.Name("getMethodBind")),
                List(cStr(cls.name), cStr(m.name), Lit.Long(m.hash))
              )
            )
        }.toList

        val bindsObjOpt: Option[Defn.Object] = Option
            .when(bindsStats.nonEmpty)(simpleObject("Binds", bindsStats))

        val ctorDefOpt: Option[Defn.Def] = Option.when(cls.isInstantiable) {
            val obj     = Term.Name("obj")
            val refStmt = Option.when(
              refcountedTypes.contains(cls.name)
            )(Term.Apply(Term.Select(obj, Term.Name("reference")), Nil).asInstanceOf[Stat])
            val ctorBody = Term.Block(
              List(
                simpleValDef("obj", newEngineAnonymous(cls.name, List(Nil))),
                Term.Assign(
                  Term.Select(obj, Term.Name("ptr")),
                  Term.Apply(
                    Term.Select(Term.Name("GdxApi"), Term.Name("constructObject")),
                    List(cStr(cls.name))
                  )
                )
              ) ++ refStmt.toList ++ List(obj)
            )
            Defn.Def(Nil, Term.Name("apply"), Nil, Nil, Some(Type.Name(cls.name)), ctorBody)
        }

        val singletonStats: List[Stat] =
            if !cls.isSingleton then Nil
            else
                val singletonVal = lazyValDef(
                  "singleton",
                  Type.Name(cls.name),
                  newEngineAnonymous(
                    cls.name,
                    List(List(Term.Apply(
                      Term.Select(Term.Name("GdxApi"), Term.Name("getSingleton")),
                      List(cStr(cls.name))
                    )))
                  )
                )
                singletonVal :: instanceMethods.map(buildForwardingMethod(cls, _, valueBuiltins))
                    .toList

        val ptrInitStat: Option[Stat] = Option
            .when(cls.inherits.isEmpty)(Term.Assign(Term.Name("ptr"), Term.Name("_p")))
        val selfNodeGiven: Option[Stat] = Option
            .when(cls.name == "Node")("given selfNode: Node = this".parse[Stat].get)

        val classStats: List[Stat] = ptrInitStat.toList ++ selfNodeGiven.toList ++
            virtualDefs.toList ++ methodDefs.toList ++ propStats

        val parentName  = cls.inherits.getOrElse("GodotObject")
        val parentArgs  = if cls.inherits.isDefined then List(List(Term.Name("_p"))) else Nil
        val parentInits = List(Init(Type.Name(parentName), Name.Anonymous(), parentArgs))
        val ctor        = Ctor.Primary(
          Nil,
          Name.Anonymous(),
          List(List(Term.Param(Nil, Term.Name("_p"), Some(ptrByte), Some(Lit.Null()))))
        )
        val classDescription = combineDescriptions(cls.briefDescription, cls.description)
        val classMarker: (String, String) =
            s"abstract class ${cls.name}(" -> formatComment(classDescription, None)
        val clsDef                        = Defn.Class(
          List(Mod.Abstract()),
          Type.Name(cls.name),
          Nil,
          ctor,
          Template(Nil, parentInits, Self(Name.Anonymous(), None), classStats)
        )

        val classType             = Type.Apply(Type.Name("GodotClass"), List(Type.Name(cls.name)))
        val godotClassGiven: Stat = buildGivenAlias(
          classType,
          Term.NewAnonymous(Template(
            Nil,
            List(Init(classType, Name.Anonymous(), List.empty[List[Term]])),
            Self(Name.Anonymous(), None),
            List(
              Defn.Def(Nil, Term.Name("className"), Nil, Nil, None, Lit.String(cls.name)),
              Defn.Def(
                Nil,
                Term.Name("isRefCounted"),
                Nil,
                Nil,
                None,
                Lit.Boolean(refcountedTypes.contains(cls.name))
              ),
              Defn.Def(
                Nil,
                Term.Name("wrap"),
                Nil,
                List(List(Term.Param(Nil, Term.Name("p"), Some(ptrByte), None))),
                None,
                newEngineAnonymous(cls.name, List(List(Term.Name("p"))))
              )
            )
          ))
        )

        val companionStats: List[Stat] = singletonStats ++ bindsObjOpt.toList ++
            ctorDefOpt.toList ++ staticDefs.toList ++ List(godotClassGiven)
        val companionOpt: Option[Defn.Object] = Some(simpleObject(cls.name, companionStats))

        val nodeExtStat: Option[Defn.ExtensionGroup] = Option.when(cls.name == "Node") {
            val scParam = Term.Param(Nil, Term.Name("sc"), Some(Type.Name("StringContext")), None)
            val usingNodeParam = buildUsingParam("node", Type.Name("Node"))
            val dollarBody     = """Zone {
              val strBuf = stackalloc[Byte](8)
              memset(strBuf, 0, 8.toUSize)
              GdxApi.initGodotString(strBuf, toCString(sc.parts.head))
              val npBuf = stackalloc[Byte](8)
              memset(npBuf, 0, 8.toUSize)
              GdxApi.initNodePath(npBuf, strBuf)
              GdxApi.destroyGodotString(strBuf)
              val result = node.getNode(NodePath(npBuf))
              GdxApi.destroyNodePath(npBuf)
              result
            }""".parse[Term].get
            val dollarMethod = Defn.Def(
              Nil,
              Term.Name("$"),
              Nil,
              List(List(
                Term.Param(Nil, Term.Name("args"), Some(Type.Repeated(Type.Name("Any"))), None)
              )),
              Some(Type.Name("Node")),
              dollarBody
            )
            buildExtensionGroup(scParam, List(usingNodeParam), List(dollarMethod))
        }

        val topStats: List[Stat] = List(clsDef) ++ companionOpt.toList ++ nodeExtStat.toList

        val baseImports = List(
          "import scala.scalanative.unsafe.*",
          "import scala.scalanative.unsigned.*",
          "import scala.scalanative.libc.stdlib.malloc",
          "import net.`julian-avar`.gdext.core.{GdxApi, GodotObject, GodotClass, Ptrcall, StringNames, CanCallApi}"
        )

        val allImports =
            if cls.name == "Node" then baseImports :+ "import scala.scalanative.libc.string.memset"
            else baseImports
        end allImports

        val printed = buildSource("net.`julian-avar`.gdext.generated", allImports, topStats)
        val markers = classMarker :: (methodMarkers ++ propMarkers)

        "// Generated by gdext generator — do not edit.\n" + injectComments(printed, markers)
    end wrapperSource
end WrappersGenerator
