package gdext.generator

import Ast.GodotArg

object Parser:
    object Extractors:
        def description(innerJson: ujson.Value): Option[String] = innerJson.obj.get("description")
            .map { _.arr.map(_.str).mkString("\n") }

        def typeDescription(innerJson: ujson.Value): Ast.TypeDescription =
            val `type`      = innerJson("type").str
            val description = this.description(innerJson)
            (`type`, description)
        end typeDescription

        def arguments(innerJson: ujson.Value): Ast.Arguments = innerJson("arguments").arr
            .map { memberJson =>
                val maybeName       = memberJson.obj.get("name").map(_.str)
                val typeDescription = this.typeDescription(memberJson)
                val description     = memberJson.obj.get("description")
                    .map(_.arr.map(_.str).mkString("\n"))
                (maybeName, typeDescription, description)
            }.toVector

        def returnValue(innerJson: ujson.Value): Option[Ast.TypeDescription] = innerJson.obj
            .get("return_value").map(typeDescription)

        def deprecated(innerJson: ujson.Value): Option[Ast.Deprecated] = innerJson.obj
            .get("deprecated").map { json =>
                Ast.Deprecated(
                  since = json("since").str,
                  message = json.obj.get("message").map(_.str).getOrElse(""),
                  replaceWith = json.obj.get("replace_with").map(_.str).getOrElse("")
                )
            }
    end Extractors

    def types(types: ujson.Value) = types.arr.map { `type` =>
        val name = `type`("name").str
        val kind = `type`("kind").str match
            case "enum" => Ast.Kind.Enum(
                  values = `type`("values").arr.map { typeValue =>
                      val name        = typeValue("name").str
                      val value       = typeValue("value").num.toInt
                      val description = typeValue.obj.get("description")
                          .map(_.arr.map(_.str).mkString("\n"))
                      (index = value, name = name, descriptionOption = description)
                  }.toVector,
                  isBitfield = `type`.obj.get("is_bitfield").exists(_.bool)
                )

            case "handle" => Ast.Kind.Handle(
                  isConst = `type`.obj.get("is_const").exists(_.bool),
                  isUninitialized = `type`.obj.get("is_uninitialized").exists(_.bool),
                  parent = `type`.obj.get("parent").map(_.str)
                )

            case "alias" => Ast.Kind.Alias(`type`("type").str)

            case "struct" => Ast.Kind.Struct(members = `type`("members").arr.map { memberJson =>
                    val name            = memberJson("name").str
                    val typeDescription = Extractors.typeDescription(memberJson)
                    (name, typeDescription)
                }.toVector)

            case "function" => Ast.Kind.Function(
                  arguments = Extractors.arguments(`type`),
                  returnValue = Extractors.returnValue(`type`)
                )

            case keyword => throw new Exception(s"Invalid kind keyword: $keyword")
        end kind

        val description = Extractors.description(`type`)
        val deprecated  = Extractors.deprecated(`type`)

        Ast.Type(name, kind, description, deprecated)
    }
    end types

    def interfaces(interfaces: ujson.Value): Vector[Ast.Interface] = interfaces.arr
        .map { interfaceJson =>
            Ast.Interface(
              name = interfaceJson("name").str,
              description = Extractors.description(interfaceJson),
              deprecated = Extractors.deprecated(interfaceJson),
              arguments = Extractors.arguments(interfaceJson),
              returnValue = Extractors.returnValue(interfaceJson),
              since = interfaceJson("since").str,
              see = interfaceJson.obj.get("see").map(_.arr.map(_.str).toVector)
                  .getOrElse(Vector.empty),
              legacyTypeName = interfaceJson.obj.get("legacy_type_name").map(_.str)
            )
        }.toVector

    // ── extension_api.json ────────────────────────────────────────────────────

    /** Map an extension_api type string to ReturnType (for virtual stub generation). */
    def toReturnType(typeName: String): Ast.ReturnType =
        import Ast.ReturnType.*
        typeName match
            case "void"                            => Void
            case "bool"                            => Bool
            case "float"                           => Float
            case "int"                             => Int
            case "String"                          => GodotString
            case "StringName"                      => StringName
            case "PackedStringArray"               => PackedStringArray
            case "Dictionary"                      => Dictionary
            case t if t.startsWith("typedarray::") => Array
            case "Array"                           => Array
            case "Variant"                         => Variant
            case "void*"                           => VoidPtr
            case t if t.startsWith("enum::")       => Int
            case t if t.startsWith("bitfield::")   => Int
            case _                                 => Object
        end match
    end toReturnType

    def singletonNames(json: ujson.Value): Set[String] = json("singletons").arr.map(_("name").str)
        .toSet

    def godotClasses(
        json: ujson.Value,
        singletonNames: Set[String] = Set.empty
    ): Vector[Ast.GodotClass] = json("classes").arr.map { cls =>
        val rawMethods = cls.obj.get("methods").map(_.arr.toVector).getOrElse(Vector.empty)

        val methods = rawMethods.map { m =>
            val rv = m.obj.get("return_value")
            Ast.GodotMethod(
              name = m("name").str,
              hash = m.obj.get("hash").map(_.num.toLong).getOrElse(0L),
              returnTypeName = rv.map(_("type").str).getOrElse("void"),
              returnMeta = rv.flatMap(_.obj.get("meta").map(_.str)),
              args = m.obj.get("arguments").map(_.arr.toVector.map { a =>
                  Ast.GodotArg(
                    name = a("name").str,
                    typeName = a("type").str,
                    meta = a.obj.get("meta").map(_.str),
                    defaultValue = a.obj.get("default_value").map(_.str)
                  )
              }).getOrElse(Vector.empty),
              isStatic = m("is_static").bool,
              isVirtual = m("is_virtual").bool,
              isRequired = m.obj.get("is_required").exists(_.bool)
            )
        }

        val properties = cls.obj.get("properties").map(_.arr.toVector.map { p =>
            Ast.GodotProperty(
              name = p("name").str,
              getter = p("getter").str,
              setter = p.obj.get("setter").map(_.str).filter(_.nonEmpty)
            )
        }).getOrElse(Vector.empty)

        val className = cls("name").str
        Ast.GodotClass(
          name = className,
          inherits = cls.obj.get("inherits").map(_.str),
          isRefcounted = cls("is_refcounted").bool,
          isInstantiable = cls("is_instantiable").bool,
          isSingleton = singletonNames.contains(className),
          methods = methods,
          properties = properties
        )
    }.toVector

    // Primitives already handled by scalaType / packArg / retSetup — no class needed.
    private val skipBuiltins =
        Set("Nil", "bool", "int", "float", "String", "StringName", "Variant", "Array")

    // ── Utility Functions ────────────────────────────────────────────────────

    case class UtilityFunction(
        name: String,
        isVararg: Boolean,
        hash: Long,
        arguments: Vector[GodotArg],
        returnTypeName: String
    )

    def utilityFunctions(json: ujson.Value): Vector[UtilityFunction] = json("utility_functions").arr
        .map { fn =>
            UtilityFunction(
              name = fn("name").str,
              isVararg = fn.obj.get("is_vararg").exists(_.bool),
              hash = fn.obj.get("hash").map(_.num.toLong).getOrElse(0L),
              arguments = fn.obj.get("arguments").map(_.arr.toVector.map { a =>
                  GodotArg(
                    name = a("name").str,
                    typeName = a("type").str,
                    meta = a.obj.get("meta").map(_.str),
                    defaultValue = a.obj.get("default_value").map(_.str)
                  )
              }).getOrElse(Vector.empty),
              returnTypeName = fn.obj.get("return_type").map(_.str).getOrElse("void")
            )
        }.toVector

    def builtinClasses(json: ujson.Value): Vector[Ast.BuiltinClass] =
        // Use float_64 as the authoritative config (Linux/Windows x86-64).
        val membersByName: Map[String, Vector[Ast.BuiltinMember]] = json(
          "builtin_class_member_offsets"
        ).arr.find(_("build_configuration").str == "float_64").map(_("classes").arr.map { cls =>
            cls("name").str -> cls("members").arr.toVector.map { m =>
                Ast.BuiltinMember(m("member").str, m("meta").str)
            }
        }.toMap).getOrElse(Map.empty)

        val sizeByName: Map[String, Int] = json("builtin_class_sizes").arr
            .find(_("build_configuration").str == "float_64").map(_("sizes").arr.map { s =>
                s("name").str -> s("size").num.toInt
            }.toMap).getOrElse(Map.empty)

        json("builtin_classes").arr.filterNot(c => skipBuiltins.contains(c("name").str)).map { c =>
            val name = c("name").str
            Ast.BuiltinClass(
              name = name,
              size = sizeByName.getOrElse(name, 0),
              members = membersByName.getOrElse(name, Vector.empty)
            )
        }.toVector
    end builtinClasses

    def typeName(toParseType: String): String =
        val baseTypeMap: Map[String, String] = Map(
          "void"     -> "CVoidPtr",
          "int8_t"   -> "CSignedChar",
          "uint8_t"  -> "UByte",
          "int16_t"  -> "Short",
          "uint16_t" -> "UShort",
          "int32_t"  -> "CInt",
          "uint32_t" -> "CUnsignedInt",
          "int64_t"  -> "CLongLong",
          "uint64_t" -> "CUnsignedLongLong",
          "size_t"   -> "CSize",
          "char"     -> "CChar",
          "char16_t" -> "CChar16",
          "char32_t" -> "CChar32",
          "wchar_t"  -> "CWideChar",
          "float"    -> "CFloat",
          "double"   -> "CDouble"
        )

        def ptrOrRaw: String =
            if toParseType.endsWith("*") then
                val rawType = toParseType.stripPrefix("const ").stripSuffix("*")
                if rawType == "void" then "CVoidPtr"
                else
                    // Always use Ptr, ignore const qualifier
                    s"Ptr[${baseTypeMap.get(rawType).getOrElse(rawType)}]"
                end if
            else toParseType
        baseTypeMap.get(toParseType).getOrElse(ptrOrRaw)
    end typeName
end Parser
