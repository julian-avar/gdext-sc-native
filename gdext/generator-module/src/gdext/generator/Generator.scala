package gdext.generator

object Generator:
    case class ScalaFile(content: String, path: String, name: String)

    def types(types: Vector[Ast.Type]): Vector[ScalaFile] = types.groupBy(_.kind.name)
        .map { (name, types) =>
            val contents = types.map { `type` =>
                val comment = util.formatComment(`type`)
                // TODO: implement per-kind code generation
                s"${comment}\n// type ${`type`.name} : ${`type`.kind.name}\n"
            }

            val content = s"""
                |package io.github.optical002.godot.codegen.gdextensioninterface.types
                |
                |import scala.scalanative.unsafe.*
                |import scala.scalanative.unsigned.*
                |import scala.scalanative.unsigned.UInt.*
                |import io.github.optical002.godot.types.*
                |
                |${contents.mkString}
                |""".stripMargin

            ScalaFile(path = "io/github/optical002/godot/codegen/gdextensioninterface/types", name = name, content = content)
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
            }.mkString

            val interfaceVars = interfaces.map { interface =>
                s"var ${interface.name}: ${{
                        getInterfaceName(interface.name)
                    }} = null.asInstanceOf[${{ getInterfaceName(interface.name) }}]"
            }.mkString("\n  ")

            // Split interfaces into groups of 20 to avoid UTF8 string too large
            val batchSize = 20
            val batches   = interfaces.grouped(batchSize).toVector

            val helperMethods = batches.zipWithIndex.map { (batch, idx) =>
                val loadAndAssign = batch.map { interface =>
                    val nameLit  = interface.name
                    val typeName = getInterfaceName(interface.name)

                    s"""result.${nameLit} = getProcAddr.apply(toCString("${nameLit}")).asInstanceOf[${typeName}]"""
                }.mkString("\n      ")

                s"""
                |  private def loadBatch${idx}(
                |    result: Interface,
                |    getProcAddr: GDExtensionInterfaceGetProcAddress
                |  )(using zone: Zone): Unit = {
                |      $loadAndAssign
                |  }
                |""".stripMargin
            }.mkString("\n")

            s"""
            |package io.github.optical002.godot.codegen.gdextensioninterface.codegen.types
            |
            |import scala.scalanative.unsafe.*
            |import scala.scalanative.unsigned.*
            |import scala.scalanative.unsigned.UInt.*
            |import io.github.optical002.godot.codegen.gdextensioninterface.types.*
            |
            |$definitions
            |
            |class Interface private() {
            |  $interfaceVars
            |}
            |object Interface {
            |$helperMethods
            |
            |  def load(
            |    getProcAddr: GDExtensionInterfaceGetProcAddress
            |  ): Interface = Zone.acquire { (zone: Zone) ?=>
            |      val result = new Interface()
            |      ${batches.indices.map(i => s"loadBatch$i(result, getProcAddr)")
                          .mkString("\n      ")}
            |      result
            |  }
            |}
            |""".stripMargin
        end content

        Vector(ScalaFile(path = "io/github/optical002/godot/codegen/gdextensioninterface/codegen/types", name = "Interface", content = content))
    end interfaces

    // ── extension_api.json: virtual stubs + wrapper classes ──────────────────

    private def defaultVirtual(ret: Ast.ReturnType): String =
        import Ast.ReturnType.*
        ret match
            case Void              => "VirtualStub.noop"
            case Bool              => "VirtualStub.returnFalse"
            case Int               => "VirtualStub.returnZeroInt"
            case Float             => "VirtualStub.returnZeroFloat"
            case GodotString       => "VirtualStub.emptyString"
            case StringName        => "VirtualStub.emptyStringName"
            case PackedStringArray => "VirtualStub.emptyPackedStringArray"
            case Dictionary        => "VirtualStub.emptyDictionary"
            case Array             => "VirtualStub.emptyArray"
            case Object            => "VirtualStub.nullObject"
            case VoidPtr           => "VirtualStub.nullVoidPtr"
            case Variant           => "VirtualStub.nilVariant"

    def classVirtuals(classes: Vector[Ast.GodotClass]): Vector[ScalaFile] =
        classes.flatMap { cls =>
            val virtuals = cls.methods.filter(_.isVirtual)
            if virtuals.isEmpty then None
            else
                val entries = virtuals.map { m =>
                    val ret  = Parser.toReturnType(m.returnTypeName)
                    val stub = defaultVirtual(ret)
                    s"""        VirtualEntry("${m.name}", required = ${m.isRequired}, default = $stub),"""
                }.mkString("\n")
                val content =
                    s"""|// Generated by gdext generator — do not edit.
                        |package gdext.generated
                        |
                        |import gdext.virtual.{VirtualEntry, VirtualStub}
                        |
                        |object ${cls.name}Virtuals:
                        |    val entries: Vector[VirtualEntry] = Vector(
                        |$entries
                        |    )
                        |""".stripMargin
                Some(ScalaFile(content = content, path = "gdext/generated", name = s"${cls.name}Virtuals"))
        }

    // ── Wrapper class generation ──────────────────────────────────────────────

    private val scalaKeywords = Set(
        "abstract", "case", "catch", "class", "def", "do", "else", "enum", "extends", "false",
        "final", "finally", "for", "if", "implicit", "import", "lazy", "match", "new",
        "null", "object", "override", "package", "private", "protected", "return",
        "sealed", "super", "this", "throw", "trait", "try", "true", "type", "val",
        "var", "while", "with", "yield"
    )

    private def safeName(n: String): String =
        if scalaKeywords.contains(n) then s"`$n`" else n

    private def safeSetterName(n: String): String =
        if scalaKeywords.contains(n) then s"`${n}_=`" else s"${n}_="

    private def toCamel(name: String): String =
        val leading = name.takeWhile(_ == '_')
        val parts   = name.dropWhile(_ == '_').split("_")
        leading + parts.zipWithIndex.map { (p, i) =>
            if i == 0 || p.isEmpty then p else p.head.toUpper.toString + p.tail
        }.mkString

    private def scalaType(godotType: String, meta: Option[String]): String =
        godotType match
            case "void"                              => "Unit"
            case "bool"                              => "Boolean"
            case "int"                               => if meta.exists(_.contains("32")) then "Int" else "Long"
            case "float"                             => if meta.contains("float") then "Float" else "Double"
            case "String" | "StringName"             => "CString"
            case t if t.startsWith("typedarray::")   => "Ptr[Byte]"
            case t if t.startsWith("enum::")         => "Int"
            case t if t.startsWith("bitfield::")     => "Int"
            case "Variant" | "void*"                 => "Ptr[Byte]"
            case t if t.endsWith("*")                => "Ptr[Byte]" // Generic pointer type
            case t                                   => t // Assume it's a class name

    private def packArg(arg: Ast.GodotArg, i: Int): (String, String) =
        val param = safeName(toCamel(arg.name))
        arg.typeName match
            case "bool" =>
                val t = s"_a$i"
                (s"val $t = stackalloc[Byte](); !$t = if $param then 1.toByte else 0.toByte",
                 s"$t.asInstanceOf[Ptr[Byte]]")
            case "int" =>
                val t    = s"_a$i"
                val cast = if arg.meta.exists(_.contains("32")) then ".toLong" else ""
                (s"val $t = stackalloc[CLong](); !$t = $param$cast",
                 s"$t.asInstanceOf[Ptr[Byte]]")
            case "float" =>
                val t    = s"_a$i"
                val cast = if arg.meta.contains("float") then ".toDouble" else ""
                (s"val $t = stackalloc[Double](); !$t = $param$cast",
                 s"$t.asInstanceOf[Ptr[Byte]]")
            case _ =>
                ("", s"$param.ptr")

    private def retSetup(godotType: String, meta: Option[String]): (String, String) =
        godotType match
            case "void"  => ("", "")
            case "bool"  => ("val _ret = stackalloc[Byte]()",   "!_ret != 0.toByte")
            case "int"   =>
                val read = if meta.exists(_.contains("32")) then "(!_ret).toInt" else "!_ret"
                ("val _ret = stackalloc[CLong]()", read)
            case "float" =>
                val read = if meta.contains("float") then "(!_ret).toFloat" else "!_ret"
                ("val _ret = stackalloc[Double]()", read)
            case t if t.startsWith("enum::") || t.startsWith("bitfield::") =>
                ("val _ret = stackalloc[CLong]()", "(!_ret).toInt")
            case t =>
                val read = t match
                    case "String" | "StringName" | "Variant" | "void*" => "!_ret"
                    case t2 if t2.startsWith("typedarray::") || t2 == "Array" => "!_ret"
                    case t2 => s"new $t2(!_ret)"
                ("val _ret = stackalloc[Ptr[Byte]]()", read)

    private def generateMethod(cls: Ast.GodotClass, m: Ast.GodotMethod): String =
        val name      = toCamel(m.name)
        val reqArgs   = m.args.filterNot(_.hasDefault)
        val paramList = reqArgs.zipWithIndex.map { (a, _) =>
            s"${safeName(toCamel(a.name))}: ${scalaType(a.typeName, a.meta)}"
        }.mkString(", ")

        val packed = reqArgs.zipWithIndex.map { (a, i) => packArg(a, i) }

        val argsBlock =
            if reqArgs.isEmpty then "        val _args = null.asInstanceOf[Ptr[Ptr[Byte]]]"
            else
                val lines = s"        val _args = stackalloc[Ptr[Byte]](${reqArgs.size})" +:
                    packed.zipWithIndex.flatMap { case ((setup, expr), i) =>
                        val set = s"        _args($i) = $expr"
                        if setup.nonEmpty then Seq(s"        $setup", set) else Seq(set)
                    }
                lines.mkString("\n")

        val (rAlloc, rRead) = retSetup(m.returnTypeName, m.returnMeta)
        val retLine         = if rAlloc.nonEmpty then s"        $rAlloc\n" else ""
        val retPtr          = if rAlloc.nonEmpty then "_ret.asInstanceOf[Ptr[Byte]]" else "null"
        val callLine        = s"        GdxApi.ptrcall(${cls.name}.Binds.${safeName(name)}, ptr, _args, $retPtr)"
        val readLine        = if rRead.nonEmpty then s"\n        $rRead" else ""

        s"    def ${safeName(name)}($paramList): ${scalaType(m.returnTypeName, m.returnMeta)} =\n$argsBlock\n$retLine$callLine$readLine"

    private def generateVirtual(m: Ast.GodotMethod): String =
        val params  = m.args.map { a =>
            s"${safeName(toCamel(a.name))}: ${scalaType(a.typeName, a.meta)}"
        }.mkString(", ")
        val default = m.returnTypeName match
            case "void" => "()"
            case "bool" => "false"
            case "int" | "float" => "0"
            case _ => "null"
        s"    def ${safeName(toCamel(m.name))}($params): ${scalaType(m.returnTypeName, m.returnMeta)} = $default"

    def generateWrappers(classes: Vector[Ast.GodotClass]): Vector[ScalaFile] =
        classes.map { cls =>
            val regular   = cls.methods.filter(m => !m.isVirtual && !m.isStatic)
            val virtuals  = cls.methods.filter(_.isVirtual)

            val methodSrc  = regular.map(generateMethod(cls, _)).mkString("\n\n")
            val virtualSrc = virtuals.map(generateVirtual).mkString("\n")
            val propSrc    = cls.properties.map { p =>
                val field  = toCamel(p.name)
                val getter = s"    def ${safeName(field)}: Ptr[Byte] = ${safeName(toCamel(p.getter))}()"
                val setter = p.setter.map { s =>
                    s"    def ${safeSetterName(field)}(v: Ptr[Byte]): Unit = ${safeName(toCamel(s))}(v)"
                }.getOrElse("")
                if setter.nonEmpty then s"$getter\n$setter" else getter
            }.mkString("\n")

            val bindsVars  = regular.map(m => s"        var ${safeName(toCamel(m.name))}: Ptr[Byte] = null").mkString("\n")
            val bindsLoads = regular.map { m =>
                val sn = safeName(toCamel(m.name))
                s"""            Binds.$sn = GdxApi.getMethodBind(c"${cls.name}", c"${m.name}", ${m.hash}L)"""
            }.mkString("\n")

            val classDef = cls.inherits match
                case Some(p) => s"class ${cls.name}(ptr: Ptr[Byte]) extends $p(ptr)"
                case None    => s"class ${cls.name}(val ptr: Ptr[Byte])"

            val ctorDef = if cls.isInstantiable then
                s"""    def apply(): ${cls.name} =
                    |        new ${cls.name}(GdxApi.constructObject(c"${cls.name}"))""".stripMargin
            else ""

            val bindsSection = if regular.nonEmpty then
                s"""    object Binds:
                   |$bindsVars
                   |
                   |        def loadBinds(): Unit =
                   |$bindsLoads""".stripMargin
            else ""

            val companionBody = Seq(bindsSection, ctorDef).filter(_.nonEmpty).mkString("\n\n")

            val content =
                s"""|// Generated by gdext generator — do not edit.
                    |package gdext.godot
                    |
                    |import scala.scalanative.unsafe.*
                    |import scala.scalanative.unsigned.*
                    |import gdext.GdxApi
                    |
                    |$classDef
                    |$virtualSrc
                    |$methodSrc
                    |$propSrc
                    |""".stripMargin +
                (if companionBody.nonEmpty then
                    s"""|
                        |object ${cls.name}:
                        |$companionBody
                        |""".stripMargin
                else "")


            ScalaFile(content = content, path = "gdext/godot", name = cls.name)
        }
    end generateWrappers

    // ---

    def functionDefinition(comment: String, name: String, function: Ast.Kind.Function): String =
        val Ast.Kind.Function(arguments, returnValue) = function

        // Generate argument types with inline parameter name comments
        val argumentTypesWithComments = arguments.zipWithIndex.map { (a, idx) =>
            val paramName = a._1.filter(_.nonEmpty).getOrElse(s"_$idx")
            val paramType = Parser.typeName(a._2._1)
            s"$paramType, // $paramName"
        }.mkString("\n  ")

        val returnType = returnValue.map(r => Parser.typeName(r._1)).getOrElse("Unit")

        s"""
        |$comment
        |type ${name} = CFuncPtr${arguments.length}[
        |  ${argumentTypesWithComments}
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
