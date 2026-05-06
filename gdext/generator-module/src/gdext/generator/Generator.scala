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
        end match
    end defaultVirtual

    def generateBuiltins(builtins: Vector[Ast.BuiltinClass]): Vector[ScalaFile] =
        val defs    = builtins.map(b => s"class ${b.name}(val ptr: Ptr[Byte])").mkString("\n")
        val content = s"""|// Generated by gdext generator — do not edit.
            |package gdext.generated
            |
            |import scala.scalanative.unsafe.*
            |
            |$defs
            |""".stripMargin
        Vector(ScalaFile(content = content, path = "gdext/generated", name = "GodotBuiltins"))
    end generateBuiltins

    def classVirtuals(classes: Vector[Ast.GodotClass]): Vector[ScalaFile] = classes.flatMap { cls =>
        val virtuals = cls.methods.filter(_.isVirtual)
        if virtuals.isEmpty then None
        else
            val entries = virtuals.map { m =>
                val ret  = Parser.toReturnType(m.returnTypeName)
                val stub = defaultVirtual(ret)
                s"""VirtualEntry("${m.name}", required = ${m.isRequired}, default = $stub)"""
            }
            val content = s"""|// Generated by gdext generator — do not edit.
                |package gdext.generated
                |
                |import gdext.virtual.{VirtualEntry, VirtualStub}
                |
                |object ${cls.name}Virtuals {
                |  val entries: Vector[VirtualEntry] = Vector(
                |    ${entries.mkString(",\n    ")}
                |  )
                |}
                |""".stripMargin
            Some(
              ScalaFile(content = content, path = "gdext/generated", name = s"${cls.name}Virtuals")
            )
        end if
    }

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

    // GodotClass declares these; generated virtuals must add `override` only when
    // both return type and arity match exactly. Value = (returnType, arity).
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

    private def scalaType(godotType: String, meta: Option[String]): String = godotType match
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
        case t                                 => t

    private def packArg(arg: Ast.GodotArg, i: Int): (String, String) =
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
            case "String" | "StringName" | "Variant" | "void*" | "Array" => ("", param)
            case t if t.startsWith("typedarray::")                       => ("", param)
            case t if t.endsWith("*")                                    => ("", param)
            case _                                                       => ("", s"$param.ptr")
        end match
    end packArg

    private def retSetup(godotType: String, meta: Option[String]): (String, String) =
        godotType match
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
            case t =>
                val read = t match
                    case "String" | "StringName" | "Variant" | "void*" | "Array" => "!_ret"
                    case t2 if t2.startsWith("typedarray::")                     => "!_ret"
                    case t2 => s"new $t2(!_ret)"
                ("val _ret = stackalloc[Ptr[Byte]]()", read)
    end retSetup

    private def generateMethod(cls: Ast.GodotClass, m: Ast.GodotMethod, indent: Int, isStatic: Boolean = false): String =
        val name      = toCamel(m.name)
        val reqArgs   = m.args.filterNot(_.hasDefault)
        val paramList = reqArgs.zipWithIndex.map { (a, _) =>
            s"${safeName(toCamel(a.name))}: ${scalaType(a.typeName, a.meta)}"
        }
        val packed = reqArgs.zipWithIndex.map { (a, i) => packArg(a, i) }

        val setupLines: Seq[String] =
            if reqArgs.isEmpty then Seq("val _args = null.asInstanceOf[Ptr[Ptr[Byte]]]")
            else
                s"val _args = stackalloc[Ptr[Byte]](${reqArgs.size})" +:
                    packed.zipWithIndex.flatMap { case ((setup, expr), i) =>
                        val set = s"_args($i) = $expr"
                        if setup.nonEmpty then Seq(setup, set) else Seq(set)
                    }

        val (rAlloc, rRead) = retSetup(m.returnTypeName, m.returnMeta)
        val retPtr          = if rAlloc.nonEmpty then "_ret.asInstanceOf[Ptr[Byte]]" else "null"
        val selfArg  = if isStatic then "null" else "ptr"
        val callLine = s"GdxApi.ptrcall(${cls.name}.Binds.${safeName(name)}, $selfArg, _args, $retPtr)"

        val bodyLines = setupLines ++ (if rAlloc.nonEmpty then Seq(rAlloc) else Seq.empty) ++
            Seq(callLine) ++ (if rRead.nonEmpty then Seq(rRead) else Seq.empty)

        val body = bodyLines.map((" " * indent) + _).mkString("\n")
        s"def ${safeName(name)}(${paramList.mkString(", ")}): ${scalaType(
              m.returnTypeName,
              m.returnMeta
            )} = {\n${body}\n  }"
    end generateMethod

    // Returns None if the virtual must be skipped (incompatible GodotClass override).
    private def generateVirtual(m: Ast.GodotMethod): Option[String] =
        val camelName = toCamel(m.name)
        val retType   = scalaType(m.returnTypeName, m.returnMeta)
        // If this name clashes with a GodotClass virtual, add `override` only when both
        // return type and arity match. If return type differs, skip the stub entirely
        // (the clash is unresolvable). If only arity differs, emit without override.
        val arity      = m.args.length
        val overrideKw = godotClassVirtuals.get(camelName) match
            case Some((expectedRet, expectedArity))
                if expectedRet == retType && expectedArity == arity => "override "
            case Some((expectedRet, _)) if expectedRet != retType => return None
            case _                                                => ""
        val params = m.args.map { a =>
            s"${safeName(toCamel(a.name))}: ${scalaType(a.typeName, a.meta)}"
        }.mkString(", ")
        val default = m.returnTypeName match
            case "void"                                                    => "()"
            case "bool"                                                    => "false"
            case "int" | "float"                                           => "0"
            case t if t.startsWith("enum::") || t.startsWith("bitfield::") => "0"
            case _                                                         => "null"
        Some(s"${overrideKw}def ${safeName(camelName)}($params): $retType = $default")
    end generateVirtual

    def generateWrappers(classes: Vector[Ast.GodotClass]): Vector[ScalaFile] = classes.map { cls =>
        val instanceMethods = cls.methods.filter { m =>
            !m.isVirtual && !m.isStatic && !jvmMethodConflicts.contains(toCamel(m.name))
        }
        val staticMethods = cls.methods.filter { m =>
            !m.isVirtual && m.isStatic && !jvmMethodConflicts.contains(toCamel(m.name))
        }
        val virtuals = cls.methods.filter(_.isVirtual)

        val methodSrc  = instanceMethods.map(generateMethod(cls, _, 4))
        val staticSrc = staticMethods.map(m => generateMethod(cls, m, 4, isStatic = true))
        val virtualSrc = virtuals.flatMap(generateVirtual)
        val propSrc    = cls.properties.flatMap { p =>
            // Only generate property shorthand when the getter is a zero-arg, non-virtual,
            // non-static method defined directly on this class (not inherited).
            cls.methods.find { m =>
                m.name == p.getter && !m.isVirtual && !m.isStatic && m.args.forall(_.hasDefault)
            }.map { gm =>
                val field            = toCamel(p.name)
                val getterReturnType = scalaType(gm.returnTypeName, gm.returnMeta)
                val getter           =
                    s"def ${safeName(field)}: $getterReturnType = ${safeName(toCamel(p.getter))}()"
                val setter = p.setter.flatMap { sName =>
                    cls.methods.find { m =>
                        m.name == sName && !m.isVirtual && !m.isStatic &&
                        m.args.count(!_.hasDefault) == 1
                    }.map { sm =>
                        val setterParamType = scalaType(sm.args.head.typeName, sm.args.head.meta)
                        s"def ${safeSetterName(field)}(v: $setterParamType): Unit = ${safeName(
                              toCamel(sName)
                            )}(v)"
                    }
                }.getOrElse("")
                if setter.nonEmpty then s"$getter\n$setter" else getter
            }
        }

        val allMethods = instanceMethods ++ staticMethods
        val bindsVars = allMethods.map(m => s"var ${safeName(toCamel(m.name))}: Ptr[Byte] = null")

        val bindsLoads = allMethods.map { m =>
            val sn = safeName(toCamel(m.name))
            s"""Binds.$sn = GdxApi.getMethodBind(c"${cls.name}", c"${m.name}", ${m.hash}L)"""
        }

        val classDef = cls.inherits match
            case Some(p) => s"class ${cls.name}(_p: Ptr[Byte] = null) extends $p(_p)"
            case None    => s"class ${cls.name}(_p: Ptr[Byte] = null) extends gdext.GodotClass"

        val ptrInit = if cls.inherits.isEmpty then "ptr = _p\n" else ""

        val ctorDef =
            if cls.isInstantiable then s"""def apply(): ${cls.name} = {
                |  val obj = new ${cls.name}()
                |  obj.ptr = GdxApi.constructObject(c"${cls.name}")
                |  obj
                |}""".stripMargin else ""

        val bindsSection =
            if allMethods.nonEmpty then s"""object Binds {
            |  ${bindsVars.mkString("\n  ")}
            |
            |  def loadBinds(): Unit = {
            |    ${bindsLoads.mkString("\n    ")}
            |  }
            |}""".stripMargin else ""

        val staticSection = if (staticSrc.nonEmpty)
            staticSrc.map("  " + _).mkString("\n\n") else ""

        val companionBody = Seq(bindsSection, ctorDef, staticSection).filter(_.nonEmpty)

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
                |import gdext.GdxApi
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

        ScalaFile(content = content, path = "gdext/generated", name = cls.name)
    }
    end generateWrappers

    // ── Utility Functions (global functions like print) ──────────────────

    def generateUtilityFunctions(utilities: Vector[Parser.UtilityFunction]): Vector[ScalaFile] =
        val methods = utilities.map { fn =>
            val name      = toCamel(fn.name)
            val reqArgs   = if fn.isVararg then Vector.empty else fn.arguments.filterNot(_.hasDefault)
            val paramList = if fn.isVararg then
                s"args: Ptr[Ptr[Byte]]"
            else
                reqArgs.zipWithIndex.map { (a, _) =>
                    s"${safeName(toCamel(a.name))}: ${scalaType(a.typeName, a.meta)}"
                }.mkString(", ")

            val setupLines: Seq[String] =
                if fn.isVararg then Seq(s"val _args = args")
                else if reqArgs.isEmpty then Seq("val _args = null.asInstanceOf[Ptr[Ptr[Byte]]]")
                else
                    val packed = reqArgs.zipWithIndex.map { (a, i) => packArg(a, i) }
                    s"val _args = stackalloc[Ptr[Byte]](${reqArgs.size})" +:
                        packed.zipWithIndex.flatMap { case ((setup, expr), i) =>
                            val set = s"_args($i) = $expr"
                            if setup.nonEmpty then Seq(setup, set) else Seq(set)
                        }

            val (rAlloc, rRead) = retSetup(fn.returnTypeName, None)
            val retPtr   = if rAlloc.nonEmpty then "_ret.asInstanceOf[Ptr[Byte]]" else "null"
            val argCount = if fn.isVararg then "-1" else reqArgs.size.toString
            val callLine = s"GdxApi.callUtilityFunction(Binds.${safeName(name)}, _args, $argCount, $retPtr)"

            val bodyLines = setupLines ++ (if rAlloc.nonEmpty then Seq(rAlloc) else Seq.empty) ++
                Seq(callLine) ++ (if rRead.nonEmpty then Seq(rRead) else Seq.empty)

            val body = bodyLines.mkString("\n    ")
            s"  def ${safeName(name)}($paramList): ${if fn.returnTypeName == "void" then "Unit" else scalaType(fn.returnTypeName, None)} = {\n    ${body}\n  }"
        }

        val bindsVars = utilities.map { fn =>
            s"var ${safeName(toCamel(fn.name))}: Ptr[Byte] = null"
        }
        val bindsLoads = utilities.map { fn =>
            val sn = safeName(toCamel(fn.name))
            s"""$sn = GdxApi.getUtilityFunctionPtr(c"${fn.name}", ${fn.hash}L)"""
        }

        val content =
            s"""|// Generated by gdext generator — do not edit.
                |package gdext.generated
                |
                |import scala.scalanative.unsafe.*
                |import scala.scalanative.unsigned.*
                |import gdext.GdxApi
                |
                |object UtilityFunctions {
                |  object Binds {
                |    ${bindsVars.mkString("\n    ")}
                |
                |    def loadBinds(): Unit = {
                |      ${bindsLoads.mkString("\n      ")}
                |    }
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
            val paramName = a._1.filter(_.nonEmpty).getOrElse(s"_$idx")
            val paramType = Parser.typeName(a._2._1)
            s"$paramType, // $paramName"
        }

        val returnType = returnValue.map(r => Parser.typeName(r._1)).getOrElse("Unit")

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
