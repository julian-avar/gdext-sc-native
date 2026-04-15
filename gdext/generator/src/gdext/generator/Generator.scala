package gdext.generator

object Generator:
    case class ScalaFile(content: String, path: String, name: String)

    def types(types: Vector[Ast.Type]): Vector[ScalaFile] = types.groupBy(_.kind.name)
        .map { (name, types) =>
            val contents =
                import util.Generatable.given
                for `type` <- types //
                yield `type`.kind.generate(`type`, util.formatComment(`type`))
            end contents

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
            end content

            ScalaFile(path = "types", name = name, content = content)
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
                |  )(implicit zone: Zone): Unit = {
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
            |import io.github.optical002.godot.types.*
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
            |  ): Interface = Zone.acquire { implicit zone: Zone =>
            |      val result = new Interface()
            |      ${batches.indices.map(i => s"loadBatch$i(result, getProcAddr)")
                  .mkString("\n      ")}
            |      result
            |  }
            |}
            |""".stripMargin
        end content

        Vector(ScalaFile(path = "interface", name = "Interface", content = content))
    end interfaces

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

    trait Generatable[Input]:
        type Self
        extension (self: Self) def generate(input: Input): String

    object Generatable:
        trait KindGeneratable extends Generatable[(`type`: Ast.Type, comment: String)]:
            extension (self: Self)
                def generate(input: (`type`: Ast.Type, comment: String)): String =
                    generate(input.`type`, input.comment)

                def generate(`type`: Ast.Type, comment: String): String
            end extension
        end KindGeneratable

        given Ast.Kind is KindGeneratable:
            extension (self: Self)
                def generate(`type`: Ast.Type, comment: String): String = self match
                    case kind: Ast.Kind.Enum     => kind.generate(`type`, comment)
                    case kind: Ast.Kind.Handle   => kind.generate(`type`, comment)
                    case kind: Ast.Kind.Alias    => kind.generate(`type`, comment)
                    case kind: Ast.Kind.Struct   => kind.generate(`type`, comment)
                    case kind: Ast.Kind.Function => kind.generate(`type`, comment)
            end extension
        end given

        given Ast.Kind.Enum is KindGeneratable:
            extension (self: Self)
                def generate(`type`: Ast.Type, comment: String): String =
                    val typeName    = if self.isBitfield then "CInt" else "CUnsignedInt"
                    val valueSuffix = if self.isBitfield then "" else ".toUInt"
                    val valuesStr   = self.values.sortBy(_.index).map { case (value, valueName) =>
                        s"  final val $valueName: ${`type`.name} = $value$valueSuffix"
                    }.mkString("\n")

                    s"""
                    |${comment}
                    |type ${`type`.name} = ${typeName}
                    |object ${`type`.name} {
                    |$valuesStr
                    |}""".stripMargin
            end extension
        end given

        given Ast.Kind.Handle is KindGeneratable:
            extension (self: Self)
                def generate(`type`: Ast.Type, comment: String): String =
                    // Always use Ptr[Byte], ignore const qualifier
                    s"""
                    |${comment}
                    |type ${`type`.name} = Ptr[Byte]
                    |""".stripMargin
            end extension
        end given

        given Ast.Kind.Alias is KindGeneratable:
            extension (self: Self) def generate(`type`: Ast.Type, comment: String): String = s"""
                |${comment}
                |type ${`type`.name} = ${Parser.typeName(self.`type`)}
                |""".stripMargin
            end extension
        end given

        given Ast.Kind.Struct is KindGeneratable:
            extension (self: Self)
                def generate(`type`: Ast.Type, comment: String): String =
                    val memberTypes   = self.members.map(m => Parser.typeName(m._2._1))
                    val memberMethods = self.members.zipWithIndex.map { case (m, idx) =>
                        val varName = if m._1 == "type" then "_type" else m._1
                        val i       = idx + 1
                        val tName   = Parser.typeName(m._2._1)
                        s"""
                        |    def ${varName}: $tName = struct._$i
                        |    def ${varName}_=(v: $tName) = struct._${i}_=(v)
                        |    def at_${varName}: Ptr[$tName] = struct.at$i
                        |""".stripMargin
                    }
                    val tagImport =
                        if memberTypes.length >= 23 then
                            "import io.github.optical002.godot.types.Tags.*"
                        else s"import Tag.materializeCStruct${memberTypes.length}Tag"
                    s"""
                    |${comment}
                    |opaque type ${`type`.name} = CStruct${self.members.length}[
                    |  ${memberTypes.mkString(",\n  ")}
                    |]
                    |object ${`type`.name} {
                    |  $tagImport
                    |
                    |  given Tag[${`type`.name}] = 
                    |    materializeCStruct${memberTypes.length}Tag[${memberTypes
                          .mkString(", ")}].asInstanceOf[Tag[${`type`.name}]]
                    |
                    |  extension (struct: ${`type`.name}) {
                    |    ${memberMethods.mkString("")}
                    |  }
                    |}""".stripMargin
            end extension
        end given

        given Ast.Kind.Function is KindGeneratable:
            extension (self: Self)
                def generate(`type`: Ast.Type, comment: String): String =
                    // Special case: GDExtensionInterfaceFunctionPtr is a generic void pointer
                    if `type`.name == "GDExtensionInterfaceFunctionPtr" //
                    then s"""
                        |${comment}
                        |type ${`type`.name} = CVoidPtr
                        |""".stripMargin
                    else
                        Generator.functionDefinition(
                          comment = comment,
                          name = `type`.name,
                          function = self
                        )
            end extension
        end given
    end Generatable
}
