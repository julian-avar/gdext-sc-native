package gdext.generator

object Generator:
    case class ScalaFile(content: String, path: String, name: String)

    def types(types: Vector[Ast.Type]): Vector[ScalaFile] = types.groupBy(_.kind.name)
        .map { (name, types) =>
            val contents = types.map { `type` =>
                val comment = util.formatComment(`type`)
                `type`.kind match
                    case Ast.Kind.Alias(underlying) =>
                        s"$comment\ntype ${`type`.name} = ${Parser.typeName(underlying)}\n"
                    case Ast.Kind.Handle(_, _, _) => s"$comment\ntype ${`type`.name} = Ptr[Byte]\n"
                    case Ast.Kind.Enum(values, isBitfield) =>
                        val baseType = if isBitfield then "CInt" else "CUnsignedInt"
                        val suffix   = if isBitfield then "" else ".toUInt"
                        val vals     = values.sortBy(_.index)
                            .map(v => s"  final val ${v.name}: ${`type`.name} = ${v.index}$suffix")
                            .mkString("\n")
                        s"$comment\ntype ${`type`.name} = $baseType\nobject ${`type`
                                .name} {\n$vals\n}\n"
                    case Ast.Kind.Struct(_) => s"$comment\ntype ${`type`.name} = Ptr[Byte]\n"
                    case Ast.Kind.Function(arguments, returnValue) => Generator.functionDefinition(
                          comment = comment,
                          name = `type`.name,
                          function = Ast.Kind.Function(arguments, returnValue)
                        )
                end match
            }

            val content = s"""
                |package gdext.generated.types
                |
                |import scala.scalanative.unsafe.*
                |import scala.scalanative.unsigned.*
                |import scala.scalanative.unsigned.UInt.*
                |
                |${contents.mkString}
                |""".stripMargin

            ScalaFile(path = "gdext/generated/types", name = name, content = content)
        }.toVector
    end types

    def interfaces(interfaces: Vector[Ast.Interface]): Vector[ScalaFile] =
        val content =
            def getInterfaceName(fromName: String) =
                s"GDExtensionInterface${fromName.split("_").map(_.capitalize).mkString}"

            val definitions = interfaces.map { interface =>
                Generator.functionDefinition(
                  comment = util.formatComment(interface.description, interface.deprecated),
                  name = getInterfaceName(interface.name),
                  function = Ast.Kind.Function(interface.arguments, interface.returnValue)
                )
            }

            val interfaceVars = interfaces.map { interface =>
                val interfaceType = getInterfaceName(interface.name)
                s"var ${interface.name}: $interfaceType = null.asInstanceOf[$interfaceType]"
            }

            // Split interfaces into groups of 20 to avoid UTF8 string too large
            val batchSize = 20
            val batches   = interfaces.grouped(batchSize).toVector

            val helperMethods = batches.zipWithIndex.map { (batch, idx) =>
                val loadAndAssign = batch.map { interface =>
                    val nameLit  = interface.name
                    val typeName = getInterfaceName(interface.name)
                    s"""result.$nameLit = getProcAddr.apply(toCString("$nameLit")).asInstanceOf[$typeName]"""
                }

                s"""
                |private def loadBatch${idx}(
                |  result: Interface,
                |  getProcAddr: GDExtensionInterfaceGetProcAddress
                |)(using zone: Zone): Unit = {
                |  ${loadAndAssign.mkString("\n  ")}
                |}
                |""".stripMargin
            }

            s"""
            |package gdext.generated
            |
            |import scala.scalanative.unsafe.*
            |import scala.scalanative.unsigned.*
            |import scala.scalanative.unsigned.UInt.*
            |import gdext.generated.types.*
            |
            |${definitions.mkString}
            |
            |class Interface private() {
            |  ${interfaceVars.mkString("\n  ")}
            |}
            |
            |object Interface {
            |  ${helperMethods.map("  " + _).mkString("\n")}
            |
            |  def load(
            |    getProcAddr: GDExtensionInterfaceGetProcAddress
            |  ): Interface = Zone.acquire { (zone: Zone) =>
            |      given Zone = zone
            |      val result = new Interface()
            |      ${batches.indices.map(i => s"loadBatch$i(result, getProcAddr)").map("      " + _)
                  .mkString("\n")}
            |      result
            |  }
            |}
            |""".stripMargin
        end content

        Vector(ScalaFile(path = "gdext/generated", name = "Interface", content = content))
    end interfaces

    // ── extension_api.json: virtual stubs + wrapper classes ──────────────────

    // ── Builtin type helpers ─────────────────────────────────────────────────

    /** True for meta types that map to a Scala primitive (direct field value). */
    private def isPrimitiveMeta(meta: String): Boolean = meta match
        case "float" | "int32" | "int64" | "double" => true
        case _                                      => false

    /** Map a builtin member meta type to its Scala Native equivalent. */
    private def metaToScalaType(meta: String): String = meta match
        case "float"  => "Float"
        case "int32"  => "Int"
        case "int64"  => "Long"
        case "double" => "Double"
        case other    => other // another builtin opaque type (e.g. Vector2, Vector3)

    /** Sort builtins so that types depended upon by others come first. */
    private def topoSort(builtins: Vector[Ast.BuiltinClass]): Vector[Ast.BuiltinClass] =
        val byName                    = builtins.map(b => b.name -> b).toMap
        val visited                   = scala.collection.mutable.LinkedHashSet.empty[String]
        def visit(name: String): Unit = if !visited.contains(name) then
            byName.get(name).foreach { b =>
                b.members.filterNot(m => isPrimitiveMeta(m.meta)).foreach(m => visit(m.meta))
                visited += name
            }
        builtins.foreach(b => visit(b.name))
        visited.toVector.flatMap(byName.get)
    end topoSort

    def generateBuiltins(builtins: Vector[Ast.BuiltinClass]): Vector[ScalaFile] =
        val sorted                  = topoSort(builtins)
        val (valueTypes, heapTypes) = sorted.partition(_.members.nonEmpty)

        def cstructTypeName(members: Vector[Ast.BuiltinMember]): String =
            val inner = members.map(m => metaToScalaType(m.meta)).mkString(", ")
            s"CStruct${members.length}[$inner]"

        def tagGiven(name: String, members: Vector[Ast.BuiltinMember]): String =
            val inner = members.map(m => metaToScalaType(m.meta)).mkString(", ")
            s"given Tag[$name] = Tag.materializeCStruct${members
                    .length}Tag[$inner].asInstanceOf[Tag[$name]]"
        end tagGiven

        // Inline apply constructor — only for all-primitive types (e.g. Vector2, Color).
        // Must be inline so stackalloc lands in the caller's frame.
        def applyCtor(name: String, members: Vector[Ast.BuiltinMember]): String =
            if !members.forall(m => isPrimitiveMeta(m.meta)) then ""
            else
                val params = members.map(m => s"${safeName(m.name)}: ${metaToScalaType(m.meta)}")
                    .mkString(", ")
                val sets = members.zipWithIndex.map { (m, i) =>
                    s"p._${i + 1} = ${safeName(m.name)}"
                }.mkString("; ")
                s"inline def apply($params): Ptr[$name] = { val p = stackalloc[$name](); $sets; p }"

        def fieldExtensions(members: Vector[Ast.BuiltinMember]): String = members.zipWithIndex
            .map { (m, i) =>
                val idx = i + 1
                val fn  = safeName(m.name)
                val tp  = metaToScalaType(m.meta)
                if isPrimitiveMeta(m.meta) then
                    s"inline def $fn: $tp = v._$idx\n    inline def ${fn}_=(value: $tp): Unit = v._$idx = value"
                else
                    // Nested value type — return Ptr so the caller can mutate in place.
                    s"inline def $fn: Ptr[$tp] = v.at$idx"
                end if
            }.mkString("\n    ")

        // Generate componentwise arithmetic for homogeneous all-primitive types (e.g. Vector2, Color).
        def mathExtensions(name: String, members: Vector[Ast.BuiltinMember]): String =
            val metas = members.map(_.meta).distinct
            if !members.forall(m => isPrimitiveMeta(m.meta)) || metas.length != 1 then return ""
            val tp          = metaToScalaType(metas.head)
            val fields      = members.map(m => safeName(m.name))
            val mapBody     = fields.map(f => s"result.$f = f(v.$f)").mkString("; ")
            val combineBody = fields.map(f => s"result.$f = f(v.$f, o.$f)").mkString("; ")
            s"""|    inline def map(f: $tp => $tp): Ptr[$name] =
                |      val result = stackalloc[$name](); $mapBody; result
                |    inline def combine(o: Ptr[$name])(f: ($tp, $tp) => $tp): Ptr[$name] =
                |      val result = stackalloc[$name](); $combineBody; result
                |    inline def *(scalar: $tp): Ptr[$name] = v.map(_ * scalar)
                |    inline def *(o: Ptr[$name]): Ptr[$name] = v.combine(o)(_ * _)
                |    inline def /(scalar: $tp): Ptr[$name] = v.map(_ / scalar)
                |    inline def /(o: Ptr[$name]): Ptr[$name] = v.combine(o)(_ / _)
                |    inline def +(scalar: $tp): Ptr[$name] = v.map(_ + scalar)
                |    inline def +(o: Ptr[$name]): Ptr[$name] = v.combine(o)(_ + _)
                |    inline def -(scalar: $tp): Ptr[$name] = v.map(_ - scalar)
                |    inline def -(o: Ptr[$name]): Ptr[$name] = v.combine(o)(_ - _)""".stripMargin
        end mathExtensions

        val valueDefs = valueTypes.map { b =>
            val cstruct   = cstructTypeName(b.members)
            val tag       = tagGiven(b.name, b.members)
            val ctor      = applyCtor(b.name, b.members)
            val exts      = fieldExtensions(b.members)
            val mathExts  = mathExtensions(b.name, b.members)
            val ctorLine  = if ctor.nonEmpty then s"\n  $ctor\n" else ""
            val mathBlock = if mathExts.nonEmpty then s"\n$mathExts" else ""
            s"""/** Godot value type. Use stackalloc[${b.name}]() or ${b
                  .name}(...) to create instances. */
               |opaque type ${b.name} = $cstruct
               |object ${b.name}:
               |  $tag
               |$ctorLine  extension (v: Ptr[${b.name}])
               |    $exts$mathBlock
               |""".stripMargin
        }.mkString("\n")

        val heapDefs = heapTypes.map(b => s"class ${b.name}(val ptr: Ptr[Byte])").mkString("\n")

        val content = s"""|// Generated by gdext generator — do not edit.
                |package gdext.generated
                |
                |import scala.scalanative.unsafe.*
                |import scala.scalanative.unsigned.*
                |
                |// ── Math value types (opaque CStruct, stack-allocatable) ──────────────────────
                |
                |$valueDefs
                |// ── Heap-reference builtins (opaque internal layout) ─────────────────────────
                |
                |$heapDefs
                |""".stripMargin

        Vector(ScalaFile(content = content, path = "gdext/generated", name = "GodotBuiltins"))
    end generateBuiltins

    /** Generate a `(_obj, _args, _ret) => { ... }` dispatch lambda for one virtual method.
      *
      * Reads each argument from the raw `Ptr[Ptr[Byte]]` array using type-appropriate casts, calls
      * the Scala method on the cast GodotObject, and writes the return value into `_ret`.
      *
      * Known limitations (tracked as future work):
      *   - String/StringName virtual params receive `null` — Godot→Scala string conversion not yet
      *     implemented.
      *   - Value-builtin and opaque return types (String, Array, Variant, …) are called but `_ret`
      *     is not written; Godot sees a zero-initialised return buffer.
      */
    private def generateDispatchLambda(
        m: Ast.GodotMethod,
        definingClass: String,
        valueBuiltins: Set[String]
    ): String =
        val camelName = toCamel(m.name)

        val argReads = m.args.zipWithIndex.map { (a, i) =>
            val read = a.typeName match
                case "bool" => s"(!_args($i).asInstanceOf[Ptr[Byte]] != 0)"
                case "int"  =>
                    if a.meta.exists(_.contains("32")) then
                        s"(!_args($i).asInstanceOf[Ptr[Long]]).toInt"
                    else s"!_args($i).asInstanceOf[Ptr[Long]]"
                case "float" =>
                    if a.meta.contains("float") then
                        s"(!_args($i).asInstanceOf[Ptr[Double]]).toFloat"
                    else s"!_args($i).asInstanceOf[Ptr[Double]]"
                case "String" | "StringName" => "null.asInstanceOf[String]"
                case t if t.startsWith("enum::") || t.startsWith("bitfield::") =>
                    s"(!_args($i).asInstanceOf[Ptr[Long]]).toInt"
                case t if valueBuiltins.contains(t)    => s"_args($i).asInstanceOf[Ptr[$t]]"
                case "Variant" | "void*" | "Array"     => s"_args($i)"
                case t if t.startsWith("typedarray::") => s"_args($i)"
                case t if t.endsWith("*")              => s"_args($i)"
                case t => s"new $t(!_args($i).asInstanceOf[Ptr[Ptr[Byte]]])"
            s"val _v$i = $read"
        }

        val argVars  = m.args.indices.map(i => s"_v$i").mkString(", ")
        val callExpr = s"_obj.asInstanceOf[$definingClass].${safeName(camelName)}($argVars)"

        val retLines = m.returnTypeName match
            case "void" => Seq(callExpr)
            case "bool" => Seq(
                  s"val _r = $callExpr",
                  "!_ret.asInstanceOf[Ptr[Byte]] = if _r then 1.toByte else 0.toByte"
                )
            case "int" if m.returnMeta.exists(_.contains("32")) =>
                Seq(s"val _r = $callExpr", "!_ret.asInstanceOf[Ptr[Long]] = _r.toLong")
            case "int" => Seq(s"val _r = $callExpr", "!_ret.asInstanceOf[Ptr[Long]] = _r")
            case "float" if m.returnMeta.contains("float") =>
                Seq(s"val _r = $callExpr", "!_ret.asInstanceOf[Ptr[Double]] = _r.toDouble")
            case "float" => Seq(s"val _r = $callExpr", "!_ret.asInstanceOf[Ptr[Double]] = _r")
            case t if t.startsWith("enum::") || t.startsWith("bitfield::") =>
                Seq(s"val _r = $callExpr", "!_ret.asInstanceOf[Ptr[Long]] = _r.toLong")
            case t
                if t == "String" || t == "StringName" || t == "PackedStringArray" || t == "Array" ||
                    t == "Variant" || t == "void*" || t.startsWith("typedarray::") ||
                    t.endsWith("*") || valueBuiltins.contains(t) =>
                Seq(callExpr) // call but skip ret write — Godot sees zero-initialised buffer
            case _ => // Object type
                Seq(
                  s"val _r = $callExpr",
                  "!_ret.asInstanceOf[Ptr[Ptr[Byte]]] = if _r != null then _r.ptr else null"
                )

        val body = (argReads ++ retLines).mkString("; ")
        s"(_obj, _args, _ret) => { $body }"
    end generateDispatchLambda

    /** Emit one `{ClassName}Virtuals.scala` per class that has virtual methods.
      *
      * Each file's `entries` covers the FULL ancestor chain — not just the methods declared
      * directly on the class — so the user only needs to pass one virtuals vector when registering.
      * Ancestor entries appear first; a subclass's own virtual shadows the ancestor entry of the
      * same name. Empty classes (no virtuals anywhere in the hierarchy) are skipped.
      */
    def classVirtuals(
        classes: Vector[Ast.GodotClass],
        valueBuiltins: Set[String] = Set.empty
    ): Vector[ScalaFile] =
        val byName = classes.map(c => c.name -> c).toMap

        // Returns (method, definingClassName) pairs for all virtuals reachable from `cls`,
        // ancestors first, with the subclass's own override shadowing the parent entry.
        def allVirtualsWithOwner(cls: Ast.GodotClass): Vector[(Ast.GodotMethod, String)] =
            val own            = cls.methods.filter(_.isVirtual).map(_ -> cls.name)
            val parentVirtuals = cls.inherits.flatMap(byName.get).map(allVirtualsWithOwner)
                .getOrElse(Vector.empty)
            val ownNames = own.map(_._1.name).toSet
            parentVirtuals.filterNot(v => ownNames.contains(v._1.name)) ++ own
        end allVirtualsWithOwner

        classes.flatMap { cls =>
            val virtuals = allVirtualsWithOwner(cls)
            if virtuals.isEmpty then None
            else
                val entries = virtuals.flatMap { (m, defClass) =>
                    // Skip virtuals that conflict with GodotObject base declarations with a
                    // different return type — generateVirtual returns None for these, so no
                    // override method exists on the class and the dispatch would fail to compile.
                    val camelName         = toCamel(m.name)
                    val retType           = scalaType(m.returnTypeName, m.returnMeta, valueBuiltins)
                    val conflictsWithBase = godotClassVirtuals.get(camelName)
                        .exists(_._1 != retType)
                    if conflictsWithBase then None
                    else
                        val dispatch = generateDispatchLambda(m, defClass, valueBuiltins)
                        Some(s"""VirtualEntry("${m.name}", required = ${m
                                .isRequired}, dispatch = $dispatch)""")
                    end if
                }
                if entries.isEmpty then None
                else
                    val content = s"""|// Generated by gdext generator — do not edit.
                        |package gdext.generated
                        |
                        |import gdext.core.virtual.VirtualEntry
                        |import gdext.core.GodotObject
                        |import scala.scalanative.unsafe.*
                        |
                        |object ${cls.name}Virtuals {
                        |  val entries: Vector[VirtualEntry] = Vector(
                        |    ${entries.mkString(",\n    ")}
                        |  )
                        |}
                        |""".stripMargin
                    Some(ScalaFile(
                      content = content,
                      path = "gdext/generated/virtuals",
                      name = s"${cls.name}Virtuals"
                    ))
                end if
            end if
        }
    end classVirtuals

    // ── Wrapper class generation ──────────────────────────────────────────────

    private val scalaKeywords = Set(
      "abstract",
      "case",
      "catch",
      "class",
      "def",
      "do",
      "else",
      "enum",
      "extends",
      "false",
      "final",
      "finally",
      "for",
      "if",
      "implicit",
      "import",
      "lazy",
      "match",
      "new",
      "null",
      "object",
      "override",
      "package",
      "private",
      "protected",
      "return",
      "sealed",
      "super",
      "this",
      "throw",
      "trait",
      "try",
      "true",
      "type",
      "val",
      "var",
      "while",
      "with",
      "yield"
    )

    // GodotObject declares these; generated virtuals must add `override` only when
    // both return type and arity match exactly. Value = (returnType, arity).
    // TODO: move these to the generated Node class once the @gdclass macro lands.
    private val godotClassVirtuals: Map[String, (String, Int)] =
        Map("_ready" -> ("Unit", 0), "_process" -> ("Unit", 1), "_physicsProcess" -> ("Unit", 1))

    // Final JVM/Scala-Native methods that cannot be overridden or redefined.
    private val jvmMethodConflicts =
        Set("wait", "notify", "notifyAll", "toString", "hashCode", "finalize", "getClass")

    private def safeName(n: String): String = if scalaKeywords.contains(n) then s"`$n`" else n

    private def safeSetterName(n: String): String =
        if scalaKeywords.contains(n) then s"`${n}_=`" else s"${n}_="

    private def toCamel(name: String): String =
        val leading = name.takeWhile(_ == '_')
        val parts   = name.dropWhile(_ == '_').split("_")
        leading + parts.zipWithIndex.map { (p, i) =>
            if i == 0 || p.isEmpty then p else p.head.toUpper.toString + p.tail
        }.mkString
    end toCamel

    private def scalaType(
        godotType: String,
        meta: Option[String],
        valueBuiltins: Set[String] = Set.empty
    ): String = godotType match
        case "void"                  => "Unit"
        case "bool"                  => "Boolean"
        case "int"                   => if meta.exists(_.contains("32")) then "Int" else "Long"
        case "float"                 => if meta.contains("float") then "Float" else "Double"
        case "String" | "StringName" => "CString"
        case t if t.startsWith("typedarray::") => "Ptr[Byte]"
        case t if t.startsWith("enum::")       => "Int"
        case t if t.startsWith("bitfield::")   => "Int"
        case "Variant" | "void*" | "Array"     => "Ptr[Byte]"
        case t if t.endsWith("*")              => "Ptr[Byte]"
        // Value types are opaque CStructs — callers hold and pass Ptr[T].
        case t if valueBuiltins.contains(t) => s"Ptr[$t]"
        case t                              => t

    /** Like scalaType but for method parameter positions. String/StringName are exposed as Scala
      * String for ergonomics; the generated method body handles the CString conversion and Godot
      * String buffer packing.
      */
    private def paramScalaType(
        godotType: String,
        meta: Option[String],
        valueBuiltins: Set[String] = Set.empty
    ): String = godotType match
        case "String" | "StringName" => "String"
        case other                   => scalaType(other, meta, valueBuiltins)

    private def packArg(
        arg: Ast.GodotArg,
        i: Int,
        valueBuiltins: Set[String] = Set.empty
    ): (String, String) =
        val param = safeName(toCamel(arg.name))
        val slot  = s"_a$i"
        arg.typeName match
            case "bool" => (
                  s"val $slot = stackalloc[Byte](); !$slot = if $param then 1.toByte else 0.toByte",
                  s"$slot.asInstanceOf[Ptr[Byte]]"
                )
            case "int" =>
                val cast = if arg.meta.exists(_.contains("32")) then ".toLong" else ""
                (
                  s"val $slot = stackalloc[Long](); !$slot = $param$cast",
                  s"$slot.asInstanceOf[Ptr[Byte]]"
                )
            case "float" =>
                val cast = if arg.meta.contains("float") then ".toDouble" else ""
                (
                  s"val $slot = stackalloc[Double](); !$slot = $param$cast",
                  s"$slot.asInstanceOf[Ptr[Byte]]"
                )
            case t if t.startsWith("enum::") || t.startsWith("bitfield::") =>
                (
                  s"val $slot = stackalloc[Long](); !$slot = $param.toLong",
                  s"$slot.asInstanceOf[Ptr[Byte]]"
                )
            case "String" =>
                // param: String — convert to Godot String (8-byte managed buffer) for ptrcall.
                // Requires Zone (implicit z) to be in scope for toCString.
                val slot = s"_a$i"
                (
                  s"val $slot = stackalloc[Byte](8); GdxApi.initGodotString($slot, toCString($param))",
                  slot
                )
            case "StringName" =>
                val slot = s"_a$i"
                (
                  s"val $slot = stackalloc[Byte](8); GdxApi.initStringName($slot, toCString($param))",
                  slot
                )
            case "Variant" | "void*" | "Array"     => ("", param)
            case t if t.startsWith("typedarray::") => ("", param)
            case t if t.endsWith("*")              => ("", param)
            // Value builtins are Ptr[T] — already a pointer, just cast to Ptr[Byte].
            case t if valueBuiltins.contains(t) => ("", s"$param.asInstanceOf[Ptr[Byte]]")
            case _                              => ("", s"$param.ptr")
        end match
    end packArg

    /** Pack an optional argument using its declared default value for ptrcall.
      *
      * GDExtension ptrcall always reads ALL arguments regardless of defaults, so we must pass a
      * valid buffer even for optional params the caller didn't supply.
      */
    private def packDefaultArg(
        arg: Ast.GodotArg,
        i: Int,
        valueBuiltins: Set[String] = Set.empty
    ): (String, String) =
        val slot    = s"_a$i"
        val default = arg.defaultValue.getOrElse("null")
        arg.typeName match
            case "bool" =>
                val bVal = if default == "true" then "1.toByte" else "0.toByte"
                (
                  s"val $slot = stackalloc[Byte](); !$slot = $bVal",
                  s"$slot.asInstanceOf[Ptr[Byte]]"
                )
            case "int" =>
                val lVal = scala.util.Try(default.toLong).getOrElse(0L)
                (
                  s"val $slot = stackalloc[Long](); !$slot = ${lVal}L",
                  s"$slot.asInstanceOf[Ptr[Byte]]"
                )
            case t if t.startsWith("enum::") || t.startsWith("bitfield::") =>
                val lVal = scala.util.Try(default.toLong).getOrElse(0L)
                (
                  s"val $slot = stackalloc[Long](); !$slot = ${lVal}L",
                  s"$slot.asInstanceOf[Ptr[Byte]]"
                )
            case "float" =>
                val dVal = scala.util.Try(default.toDouble).getOrElse(0.0)
                (
                  s"val $slot = stackalloc[Double](); !$slot = $dVal",
                  s"$slot.asInstanceOf[Ptr[Byte]]"
                )
            case "String" =>
                (s"val $slot = stackalloc[Byte](8); GdxApi.initGodotString($slot, c\"\")", s"$slot")
            case "StringName" =>
                (s"val $slot = stackalloc[Byte](8); GdxApi.initStringName($slot, c\"\")", s"$slot")
            case _ if default == "null" =>
                (
                  s"val $slot = stackalloc[Ptr[Byte]](); !$slot = null",
                  s"$slot.asInstanceOf[Ptr[Byte]]"
                )
            case _ =>
                // Zero-initialized 24-byte fallback for complex builtin defaults (Color, Rect2, etc.)
                (s"val $slot = stackalloc[Byte](24)", s"$slot")
        end match
    end packDefaultArg

    private def retSetup(
        godotType: String,
        meta: Option[String],
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty
    ): (String, String) = godotType match
        case "void" => ("", "")
        case "bool" => ("val _ret = stackalloc[Byte]()", "!_ret != 0.toByte")
        case "int"  =>
            val read = if meta.exists(_.contains("32")) then "(!_ret).toInt" else "!_ret"
            ("val _ret = stackalloc[Long]()", read)
        case "float" =>
            val read = if meta.contains("float") then "(!_ret).toFloat" else "!_ret"
            ("val _ret = stackalloc[Double]()", read)
        case t if t.startsWith("enum::") || t.startsWith("bitfield::") =>
            ("val _ret = stackalloc[Long]()", "(!_ret).toInt")
        // Value builtins: heap-allocate the return buffer so the pointer stays valid after return.
        // stackalloc would produce a dangling pointer once the method's stack frame is freed.
        case t if valueBuiltins.contains(t) =>
            (s"val _ret = malloc(sizeof[$t]).asInstanceOf[Ptr[$t]]", "_ret")
        case t =>
            val read = t match
                case "String" | "StringName" | "Variant" | "void*" | "Array" => "!_ret"
                case t2 if t2.startsWith("typedarray::")                     => "!_ret"
                case t2 if refcountedTypes.contains(t2)                      =>
                    s"""{ val _r = new $t2(!_ret); if _r.ptr != null then _r.reference(); _r }"""
                case t2 => s"new $t2(!_ret)"
            ("val _ret = stackalloc[Ptr[Byte]]()", read)
    end retSetup

    private def generateForwardingMethod(
        cls: Ast.GodotClass,
        m: Ast.GodotMethod,
        valueBuiltins: Set[String] = Set.empty
    ): String =
        val name      = toCamel(m.name)
        val reqArgs   = m.args.filterNot(_.hasDefault)
        val paramList = reqArgs.map { a =>
            s"${safeName(toCamel(a.name))}: ${paramScalaType(a.typeName, a.meta, valueBuiltins)}"
        }
        val argList = reqArgs.map(a => safeName(toCamel(a.name))).mkString(", ")
        val retType = scalaType(m.returnTypeName, m.returnMeta, valueBuiltins)
        s"def ${safeName(name)}(${paramList.mkString(", ")}): $retType = singleton.${safeName(
              name
            )}($argList)"
    end generateForwardingMethod

    private def generateMethod(
        cls: Ast.GodotClass,
        m: Ast.GodotMethod,
        indent: Int,
        isStatic: Boolean = false,
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty
    ): String =
        val name    = toCamel(m.name)
        val reqArgs = m.args.filterNot(_.hasDefault)
        val optArgs = m.args.filter(_.hasDefault)

        val paramList = reqArgs.zipWithIndex.map { (a, _) =>
            s"${safeName(toCamel(a.name))}: ${paramScalaType(a.typeName, a.meta, valueBuiltins)}"
        }
        // Required args occupy slots 0..reqArgs.size-1; optional args follow with their defaults.
        // GDExtension ptrcall reads ALL argument slots regardless of defaults.
        val reqPacked = reqArgs.zipWithIndex.map { (a, i) => packArg(a, i, valueBuiltins) }
        val optPacked = optArgs.zipWithIndex.map { (a, i) =>
            packDefaultArg(a, reqArgs.size + i, valueBuiltins)
        }
        val allPacked = reqPacked ++ optPacked

        val setupLines: Seq[String] =
            if m.args.isEmpty then Seq("val _args = null.asInstanceOf[Ptr[Ptr[Byte]]]")
            else
                s"val _args = stackalloc[Ptr[Byte]](${m.args.size})" +:
                    allPacked.zipWithIndex.flatMap { case ((setup, expr), i) =>
                        val set = s"_args($i) = $expr"
                        if setup.nonEmpty then Seq(setup, set) else Seq(set)
                    }

        val (rAlloc, rRead) =
            retSetup(m.returnTypeName, m.returnMeta, valueBuiltins, refcountedTypes)
        val retPtr   = if rAlloc.nonEmpty then "_ret.asInstanceOf[Ptr[Byte]]" else "null"
        val selfArg  = if isStatic then "null" else "ptr"
        val callLine =
            s"GdxApi.ptrcall(${cls.name}.Binds.${safeName(name)}, $selfArg, _args, $retPtr)"

        val bodyLines = setupLines ++ (if rAlloc.nonEmpty then Seq(rAlloc) else Seq.empty) ++
            Seq(callLine) ++ (if rRead.nonEmpty then Seq(rRead) else Seq.empty)

        val needsZone = reqArgs.exists(a => a.typeName == "String" || a.typeName == "StringName")
        val body      = bodyLines.map((" " * indent) + _).mkString("\n")
        val retType   = scalaType(m.returnTypeName, m.returnMeta, valueBuiltins)
        val methodSig = s"def ${safeName(name)}(${paramList.mkString(", ")}): $retType"
        if needsZone then s"$methodSig = Zone {\n${body}\n  }" else s"$methodSig = {\n${body}\n  }"
    end generateMethod

    // Returns None if the virtual must be skipped (incompatible GodotObject override).
    private def generateVirtual(
        m: Ast.GodotMethod,
        valueBuiltins: Set[String] = Set.empty
    ): Option[String] =
        val camelName = toCamel(m.name)
        val retType   = scalaType(m.returnTypeName, m.returnMeta, valueBuiltins)
        // If this name clashes with a GodotObject virtual, add `override` only when both
        // return type and arity match. If return type differs, skip the stub entirely
        // (the clash is unresolvable). If only arity differs, emit without override.
        val arity      = m.args.length
        val overrideKw = godotClassVirtuals.get(camelName) match
            case Some((expectedRet, expectedArity))
                if expectedRet == retType && expectedArity == arity => "override "
            case Some((expectedRet, _)) if expectedRet != retType => return None
            case _                                                => ""
        val params = m.args.map { a =>
            s"${safeName(toCamel(a.name))}: ${paramScalaType(a.typeName, a.meta, valueBuiltins)}"
        }.mkString(", ")
        val default = m.returnTypeName match
            case "void"                                                    => "()"
            case "bool"                                                    => "false"
            case "int" | "float"                                           => "0"
            case t if t.startsWith("enum::") || t.startsWith("bitfield::") => "0"
            case _                                                         => "null"
        Some(s"${overrideKw}def ${safeName(camelName)}($params): $retType = $default")
    end generateVirtual

    def generateWrappers(
        classes: Vector[Ast.GodotClass],
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty
    ): Vector[ScalaFile] = classes.map { cls =>
        val instanceMethods = cls.methods.filter { m =>
            !m.isVirtual && !m.isStatic && !jvmMethodConflicts.contains(toCamel(m.name))
        }
        val staticMethods = cls.methods.filter { m =>
            !m.isVirtual && m.isStatic && !jvmMethodConflicts.contains(toCamel(m.name))
        }
        val virtuals = cls.methods.filter(_.isVirtual)

        val methodSrc = instanceMethods.map(generateMethod(
          cls,
          _,
          4,
          valueBuiltins = valueBuiltins,
          refcountedTypes = refcountedTypes
        ))
        val staticSrc = staticMethods.map(m =>
            generateMethod(
              cls,
              m,
              4,
              isStatic = true,
              valueBuiltins = valueBuiltins,
              refcountedTypes = refcountedTypes
            )
        )
        val virtualSrc = virtuals.flatMap(generateVirtual(_, valueBuiltins))
        val propSrc    = cls.properties.flatMap { p =>
            // Only generate property shorthand when the getter is a zero-arg, non-virtual,
            // non-static method defined directly on this class (not inherited).
            cls.methods.find { m =>
                m.name == p.getter && !m.isVirtual && !m.isStatic && m.args.forall(_.hasDefault)
            }.map { gm =>
                val field            = toCamel(p.name)
                val getterReturnType = scalaType(gm.returnTypeName, gm.returnMeta, valueBuiltins)
                val getter           =
                    s"def ${safeName(field)}: $getterReturnType = ${safeName(toCamel(p.getter))}()"
                val setter = p.setter.flatMap { sName =>
                    cls.methods.find { m =>
                        m.name == sName && !m.isVirtual && !m.isStatic &&
                        m.args.count(!_.hasDefault) == 1
                    }.map { sm =>
                        val setterParamType =
                            paramScalaType(sm.args.head.typeName, sm.args.head.meta, valueBuiltins)
                        s"def ${safeSetterName(field)}(v: $setterParamType): Unit = ${safeName(
                              toCamel(sName)
                            )}(v)"
                    }
                }.getOrElse("")
                if setter.nonEmpty then s"$getter\n$setter" else getter
            }
        }

        val allMethods = instanceMethods ++ staticMethods
        val bindsVars  = allMethods.map { m =>
            val sn = safeName(toCamel(m.name))
            s"""lazy val $sn: Ptr[Byte] = GdxApi.getMethodBind(c"${cls.name}", c"${m.name}", ${m
                    .hash}L)"""
        }

        val classDef = cls.inherits match
            case Some(p) => s"class ${cls.name}(_p: Ptr[Byte] = null) extends $p(_p)"
            case None    => s"class ${cls.name}(_p: Ptr[Byte] = null) extends GodotObject"

        val ptrInit = if cls.inherits.isEmpty then "ptr = _p\n" else ""

        val ctorDef =
            if cls.isInstantiable then
                val refCall = if refcountedTypes.contains(cls.name) then "obj.reference()" else ""
                s"""def apply(): ${cls.name} = {
                    |  val obj = new ${cls.name}()
                    |  obj.ptr = GdxApi.constructObject(c"${cls.name}")
                    |  $refCall
                    |  obj
                    |}""".stripMargin.replaceAll("\n\\s*\n", "\n").trim
            else ""

        val bindsSection =
            if allMethods.nonEmpty then s"""object Binds {
            |  ${bindsVars.mkString("\n  ")}
            |}""".stripMargin else ""

        val staticSection =
            if staticSrc.nonEmpty then staticSrc.map("  " + _).mkString("\n\n") else ""

        val singletonSection =
            if !cls.isSingleton then ""
            else
                val forwardingMethods = instanceMethods
                    .map(m => "  " + generateForwardingMethod(cls, m, valueBuiltins))
                s"""lazy val singleton: ${cls.name} = new ${cls.name}(GdxApi.getSingleton(c"${cls
                      .name}"))
                   |${forwardingMethods.mkString("\n")}""".stripMargin

        val companionBody = Seq(singletonSection, bindsSection, ctorDef, staticSection)
            .filter(_.nonEmpty)

        val bodyParts = Seq(
          virtualSrc.mkString("\n"),
          methodSrc.map("  " + _).mkString("\n\n"),
          propSrc.mkString("\n")
        ).filter(_.nonEmpty)
        val classBody =
            if bodyParts.isEmpty && ptrInit.isEmpty then Seq.empty else ptrInit +: bodyParts

        val content =
            s"""|// Generated by gdext generator — do not edit.
                |package gdext.generated
                |
                |import scala.scalanative.unsafe.*
                |import scala.scalanative.unsigned.*
                |import scala.scalanative.libc.stdlib.malloc
                |import gdext.core.{GdxApi, GodotObject}
                |
                |$classDef ${
                   if classBody.nonEmpty then s"{\n${classBody.map("  " + _).mkString("\n")}\n}"
                   else ""
               }
                |""".stripMargin + (if companionBody.nonEmpty then s"""|
                    |object ${cls.name} {
                    |  ${companionBody.mkString("\n\n")}
                    |}
                    |""".stripMargin else "")

        ScalaFile(content = content, path = "gdext/generated/classes", name = cls.name)
    }
    end generateWrappers

    // ── Utility Functions (global functions like print) ──────────────────

    def generateUtilityFunctions(
        utilities: Vector[Parser.UtilityFunction],
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty
    ): Vector[ScalaFile] =
        val methods = utilities.map { fn =>
            val name    = toCamel(fn.name)
            val reqArgs = if fn.isVararg then Vector.empty else fn.arguments.filterNot(_.hasDefault)
            val optArgs = if fn.isVararg then Vector.empty else fn.arguments.filter(_.hasDefault)
            val allArgs = if fn.isVararg then Vector.empty else fn.arguments
            val paramList =
                if fn.isVararg then s"args: Ptr[Ptr[Byte]]"
                else
                    reqArgs.zipWithIndex.map { (a, _) =>
                        s"${safeName(
                              toCamel(a.name)
                            )}: ${paramScalaType(a.typeName, a.meta, valueBuiltins)}"
                    }.mkString(", ")

            val setupLines: Seq[String] =
                if fn.isVararg then Seq(s"val _args = args")
                else if allArgs.isEmpty then Seq("val _args = null.asInstanceOf[Ptr[Ptr[Byte]]]")
                else
                    val reqPacked = reqArgs.zipWithIndex.map { (a, i) =>
                        packArg(a, i, valueBuiltins)
                    }
                    val optPacked = optArgs.zipWithIndex.map { (a, i) =>
                        packDefaultArg(a, reqArgs.size + i, valueBuiltins)
                    }
                    s"val _args = stackalloc[Ptr[Byte]](${allArgs.size})" +:
                        (reqPacked ++ optPacked).zipWithIndex.flatMap { case ((setup, expr), i) =>
                            val set = s"_args($i) = $expr"
                            if setup.nonEmpty then Seq(setup, set) else Seq(set)
                        }

            val (rAlloc, rRead) = retSetup(fn.returnTypeName, None, valueBuiltins, refcountedTypes)
            val retPtr          = if rAlloc.nonEmpty then "_ret.asInstanceOf[Ptr[Byte]]" else "null"
            val argCount        = if fn.isVararg then "-1" else allArgs.size.toString
            val callLine        =
                s"GdxApi.callUtilityFunction(Binds.${safeName(name)}, _args, $argCount, $retPtr)"

            val bodyLines = setupLines ++ (if rAlloc.nonEmpty then Seq(rAlloc) else Seq.empty) ++
                Seq(callLine) ++ (if rRead.nonEmpty then Seq(rRead) else Seq.empty)

            val needsZone = !fn.isVararg &&
                reqArgs.exists(a => a.typeName == "String" || a.typeName == "StringName")
            val retType =
                if fn.returnTypeName == "void" then "Unit"
                else scalaType(fn.returnTypeName, None, valueBuiltins)
            val body = bodyLines.mkString("\n    ")
            val sig  = s"  def ${safeName(name)}($paramList): $retType"
            if needsZone then s"$sig = Zone {\n    $body\n  }" else s"$sig = {\n    $body\n  }"
        }

        val bindsVars = utilities.map { fn =>
            val sn = safeName(toCamel(fn.name))
            s"""lazy val $sn: Ptr[Byte] = GdxApi.getUtilityFunctionPtr(c"${fn.name}", ${fn
                    .hash}L)"""
        }

        val content = s"""|// Generated by gdext generator — do not edit.
                |package gdext.generated
                |
                |import scala.scalanative.unsafe.*
                |import scala.scalanative.unsigned.*
                |import gdext.core.GdxApi
                |
                |object UtilityFunctions {
                |  object Binds {
                |    ${bindsVars.mkString("\n    ")}
                |  }
                |
                |  ${methods.mkString("\n\n  ")}
                |}
                |""".stripMargin

        Vector(ScalaFile(content = content, path = "gdext/generated", name = "UtilityFunctions"))
    end generateUtilityFunctions

    // ---

    def functionDefinition(comment: String, name: String, function: Ast.Kind.Function): String =
        val Ast.Kind.Function(arguments, returnValue) = function

        // Generate argument types with inline parameter name comments
        val argumentTypesWithComments = arguments.zipWithIndex.map { (a, idx) =>
            val paramName = a.varNameOption.filter(_.nonEmpty).getOrElse(s"_$idx")
            val paramType = Parser.typeName(a.typeDescription.typeName)
            s"$paramType, // $paramName"
        }

        val returnType = returnValue.map(r => Parser.typeName(r.typeName)).getOrElse("Unit")

        s"""
        |$comment
        |type ${name} = CFuncPtr${arguments.length}[
        |  ${argumentTypesWithComments.map("  " + _).mkString("\n")}
        |  $returnType
        |]
        |""".stripMargin
    end functionDefinition
end Generator

package util {
    def formatComment(description: Option[String], deprecated: Option[Ast.Deprecated]) =
        if description.isEmpty && deprecated.isEmpty then ""
        else
            Vector(
              Vector("/**"),
              description.toVector.flatMap { desc => desc.split("\n").map { line => s" * $line" } },
              deprecated.toVector.flatMap { dep =>
                  Vector(
                    " *",
                    s" * @deprecated Since ${dep.since}. Use ${dep.replaceWith} instead."
                  )
              },
              Vector(" */")
            ).flatten.mkString("\n")

    def formatComment(`type`: Ast.Type): String =
        formatComment(`type`.description, `type`.deprecated)

    // trait Generatable[Input]:
    //     type Self
    //     extension (self: Self) def generate(input: Input): String

    // object Generatable:
    //     trait KindGeneratable extends Generatable[KindGeneratable.Input]
    //     object KindGeneratable:
    //         type Input = (`type`: Ast.Type, comment: String)

    //     // given Ast.Kind is KindGeneratable:
    //     //     extension (self: Self)
    //     //         def generate(`type`: Ast.Type, comment: String): String = self match
    //     //             case kind: Ast.Kind.Enum     => kind.generate(`type`, comment)
    //     //             case kind: Ast.Kind.Handle   => kind.generate(`type`, comment)
    //     //             case kind: Ast.Kind.Alias    => kind.generate(`type`, comment)
    //     //             case kind: Ast.Kind.Struct   => kind.generate(`type`, comment)
    //     //             case kind: Ast.Kind.Function => kind.generate(`type`, comment)
    //     //     end extension
    //     // end given

    //     given Ast.Kind.Enum is KindGeneratable:
    //         extension (self: Self)
    //             def generate(input: KindGeneratable.Input): String =
    //                 val ((`type`, comment)) = input
    //                 val typeName            = if self.isBitfield then "CInt" else "CUnsignedInt"
    //                 val valueSuffix         = if self.isBitfield then "" else ".toUInt"
    //                 val valuesStr = self.values.sortBy(_.index).map { case (value, valueName) =>
    //                     s"  final val $valueName: ${`type`.name} = $value$valueSuffix"
    //                 }.mkString("\n")

    //                 s"""
    //                 |${comment}
    //                 |type ${`type`.name} = ${typeName}
    //                 |object ${`type`.name} {
    //                 |$valuesStr
    //                 |}""".stripMargin
    //         end extension
    //     end given

    //     given Ast.Kind.Handle is KindGeneratable:
    //         extension (self: Self)
    //             def generate(`type`: Ast.Type, comment: String): String =
    //                 // Always use Ptr[Byte], ignore const qualifier
    //                 s"""
    //                 |${comment}
    //                 |type ${`type`.name} = Ptr[Byte]
    //                 |""".stripMargin
    //         end extension
    //     end given

    //     given Ast.Kind.Alias is KindGeneratable:
    //         extension (self: Self) def generate(`type`: Ast.Type, comment: String): String = s"""
    //             |${comment}
    //             |type ${`type`.name} = ${Parser.typeName(self.`type`)}
    //             |""".stripMargin
    //         end extension
    //     end given

    //     given Ast.Kind.Struct is KindGeneratable:
    //         extension (self: Self)
    //             def generate(`type`: Ast.Type, comment: String): String =
    //                 val memberTypes   = self.members.map(m => Parser.typeName(m._2._1))
    //                 val memberMethods = self.members.zipWithIndex.map { case (m, idx) =>
    //                     val varName = if m._1 == "type" then "_type" else m._1
    //                     val i       = idx + 1
    //                     val tName   = Parser.typeName(m._2._1)
    //                     s"""
    //                     |    def ${varName}: $tName = struct._$i
    //                     |    def ${varName}_=(v: $tName) = struct._${i}_=(v)
    //                     |    def at_${varName}: Ptr[$tName] = struct.at$i
    //                     |""".stripMargin
    //                 }
    //                 val tagImport =
    //                     if memberTypes.length >= 23 then
    //                         "import io.github.optical002.godot.types.Tags.*"
    //                     else s"import Tag.materializeCStruct${memberTypes.length}Tag"
    //                 s"""
    //                 |${comment}
    //                 |opaque type ${`type`.name} = CStruct${self.members.length}[
    //                 |  ${memberTypes.mkString(",\n  ")}
    //                 |]
    //                 |object ${`type`.name} {
    //                 |  $tagImport
    //                 |
    //                 |  given Tag[${`type`.name}] =
    //                 |    materializeCStruct${memberTypes.length}Tag[${memberTypes
    //                       .mkString(", ")}].asInstanceOf[Tag[${`type`.name}]]
    //                 |
    //                 |  extension (struct: ${`type`.name}) {
    //                 |    ${memberMethods.mkString("")}
    //                 |  }
    //                 |}""".stripMargin
    //         end extension
    //     end given

    //     given Ast.Kind.Function is KindGeneratable:
    //         extension (self: Self)
    //             def generate(`type`: Ast.Type, comment: String): String =
    //                 // Special case: GDExtensionInterfaceFunctionPtr is a generic void pointer
    //                 if `type`.name == "GDExtensionInterfaceFunctionPtr" //
    //                 then s"""
    //                     |${comment}
    //                     |type ${`type`.name} = CVoidPtr
    //                     |""".stripMargin
    //                 else
    //                     Generator.functionDefinition(
    //                       comment = comment,
    //                       name = `type`.name,
    //                       function = self
    //                     )
    //         end extension
    //     end given
}
