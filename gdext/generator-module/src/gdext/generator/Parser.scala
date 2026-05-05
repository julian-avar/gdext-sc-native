package gdext.generator

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
                (maybeName, typeDescription)
            }.toVector

        def returnValue(innerJson: ujson.Value): Option[Ast.TypeDescription] = innerJson.obj
            .get("return_value").map(typeDescription)

        def deprecated(innerJson: ujson.Value): Option[Ast.Deprecated] = innerJson.obj
            .get("deprecated").map { json =>
                Ast.Deprecated(
                  since = json("since").str,
                  replaceWith = json.obj.get("replace_with").map(_.str).getOrElse("")
                )
            }
    end Extractors

    def types(types: ujson.Value) = types.arr.map { `type` =>
        val name = `type`("name").str
        val kind = `type`("kind").str match
            case "enum" => Ast.Kind.Enum(
                  values = `type`("values").arr.map { typeValue =>
                      val name  = typeValue("name").str
                      val value = typeValue("value").num.toInt
                      (index = value, name = name)
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

    def typeName(toParseType: String): String =
        val baseTypeMap: Map[String, String] = Vector(
          ("void"     -> "CVoidPtr"),
          ("int8_t"   -> "CSignedChar"),
          ("uint8_t"  -> "UByte"),
          ("int16_t"  -> "Short"),
          ("uint16_t" -> "UShort"),
          ("int32_t"  -> "CInt"),
          ("uint32_t" -> "CUnsignedInt"),
          ("int64_t"  -> "CLongLong"),
          ("uint64_t" -> "CUnsignedLongLong"),
          ("size_t"   -> "CSize"),
          ("char"     -> "CChar"),
          ("char16_t" -> "CChar16"),
          ("char32_t" -> "CChar32"),
          ("wchar_t"  -> "CWideChar"),
          ("float"    -> "CFloat"),
          ("double"   -> "CDouble")
        ).toMap

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
