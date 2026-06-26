package gdext.core

import scala.quoted.*
import scala.scalanative.unsafe.*
import gdext.core.virtual.VirtualEntry
import gdext.core.method.MethodEntry

object Register:
    /** Scan T at compile time and emit all GdClassRegistry.register calls.
      *
      * T must extend a generated Godot engine class. The macro derives the Godot parent class from
      * the Scala superclass, detects overridden virtuals, processes `@gdexport` and `@func`
      * annotations on fields/methods, and handles inspector section markers (`@export_group` etc.).
      *
      * Usage (in the entry point):
      * {{{
      * Register.auto[PlayerSc]()
      * }}}
      */
    inline def auto[T](): Unit = ${ autoImpl[T] }

    private def autoImpl[T: Type](using Quotes): Expr[Unit] =
        import quotes.reflect.*

        val tpe       = TypeRepr.of[T]
        val sym       = tpe.typeSymbol
        val className = sym.name

        // ── Base class (Godot parent) ────────────────────────────────────────
        val baseName: String = sym.typeRef.baseClasses.drop(1)
            .find(s => !s.flags.is(Flags.Trait))
            .map(_.name)
            .getOrElse(report.errorAndAbort(
              s"$className must extend a Godot engine class (e.g. `class $className extends Node2D`)"
            ))

        // ── isRuntime ────────────────────────────────────────────────────────
        // Node subclasses → true (editor skips _ready/_process while editing scenes).
        // Resource/Object subclasses → false (editor needs real instances; placeholders break reload).
        val isRuntime: Boolean = tpe.baseClasses.drop(1).exists(_.name == "Node")

        // ── Factory ───────────────────────────────────────────────────────────
        // Build `() => new T(arg0, arg1, ...)` where each arg is either the param's Scala default
        // (looked up on the companion module) or `DefaultValue.of[ParamType]` when no Scala
        // default exists. Params that have neither cause a compile-time error.
        val ctorSym   = sym.primaryConstructor
        val allParams = ctorSym.paramSymss.flatten.filterNot(_.flags.is(Flags.Given))

        // Scala 3 names the primary-constructor default accessor `$N` (1-based), but the exact
        // mangled prefix varies by compiler version. Search by suffix rather than assuming a name.
        def findDefaultAccessor(idx: Int): Option[Symbol] =
            val suffix = s"$$default$$${idx + 1}"
            val companion = sym.companionModule
            if companion == Symbol.noSymbol then None
            else companion.declaredMethods.find(_.name.endsWith(suffix))

        val ctorArgTerms: List[Term] = allParams.zipWithIndex.map { (param, idx) =>
            val scalaDefault: Option[Term] =
                if param.flags.is(Flags.HasDefault) then
                    findDefaultAccessor(idx).map(dm => Select(Ref(sym.companionModule), dm))
                else None

            scalaDefault.getOrElse {
                // No usable Scala default — try DefaultValue[A].
                param.tree match
                    case vd: ValDef => vd.tpt.tpe.asType match
                            case '[pt] =>
                                Expr.summon[DefaultValue[pt]] match
                                    case Some(dv) => '{ $dv.default }.asTerm
                                    case None =>
                                        report.errorAndAbort(
                                          s"@gdclass $className: constructor param '${param.name}: " +
                                              s"${vd.tpt.tpe.show}' has no Scala default and no " +
                                              s"DefaultValue[${vd.tpt.tpe.show}] given. " +
                                              s"Add `= someDefault` or provide a DefaultValue given."
                                        )
                    case _ =>
                        report.errorAndAbort(
                          s"@gdclass $className: cannot determine type of param '${param.name}'"
                        )
            }
        }

        val factoryExpr: Expr[() => GodotObject] =
            '{ () =>
                ${
                    Apply(Select(New(Inferred(tpe)), ctorSym), ctorArgTerms).asExprOf[GodotObject]
                }
            }

        // ── Virtuals ─────────────────────────────────────────────────────────
        def camelToSnake(name: String): String =
            val sb = new StringBuilder
            for c <- name do if c.isUpper then sb ++= "_" + c.toLower.toString else sb += c
            sb.toString

        val userMethodNames: Set[String] = sym.declaredMethods
            .filterNot(_.flags.is(Flags.Synthetic)).map(m => camelToSnake(m.name)).toSet

        val overriddenNamesExpr: Expr[Set[String]] = Expr(userMethodNames)

        val virtualsExpr: Expr[Vector[VirtualEntry]] =
            val godotBase = if baseName == "GodotObject" then "Object" else baseName
            val modName   = s"gdext.generated.${godotBase}Virtuals"
            val modSym    = Symbol.requiredModule(modName)
            Select.unique(Ref(modSym), "entries").asExprOf[Vector[VirtualEntry]]

        val filteredVirtualsExpr: Expr[Vector[VirtualEntry]] =
            '{ $virtualsExpr.filter(e => $overriddenNamesExpr(e.name)) }

        // ── Annotation type symbols ───────────────────────────────────────────
        val gdexportSym  = TypeRepr.of[gdexport].typeSymbol
        val funcSym      = TypeRepr.of[func].typeSymbol
        val groupSym     = TypeRepr.of[export_group].typeSymbol
        val subgroupSym  = TypeRepr.of[export_subgroup].typeSymbol
        val categorySym  = TypeRepr.of[export_category].typeSymbol
        val onreadySym   = TypeRepr.of[onready].typeSymbol
        val signalSym    = TypeRepr.of[signal].typeSymbol

        // ── Inspector section marker items for a field ────────────────────────
        // Returns marker PropertyItems to emit BEFORE a field's own Prop item.
        // Annotation order: Category → Group → Subgroup (so they stack top-down).
        def markerItems(f: Symbol): List[Expr[PropertyItem]] =
            val items = scala.collection.mutable.ListBuffer.empty[Expr[PropertyItem]]
            f.getAnnotation(categorySym).foreach {
                case Apply(_, Literal(StringConstant(name)) :: _) =>
                    items += '{ PropertyItem.Category(${ Expr(name) }) }
                case _ => ()
            }
            f.getAnnotation(groupSym).foreach {
                case Apply(_, Literal(StringConstant(name)) :: rest) =>
                    val prefix = rest.headOption.collect {
                        case Literal(StringConstant(p)) => p
                    }.getOrElse("")
                    items += '{ PropertyItem.Group(${ Expr(name) }, ${ Expr(prefix) }) }
                case _ => ()
            }
            f.getAnnotation(subgroupSym).foreach {
                case Apply(_, Literal(StringConstant(name)) :: rest) =>
                    val prefix = rest.headOption.collect {
                        case Literal(StringConstant(p)) => p
                    }.getOrElse("")
                    items += '{ PropertyItem.Subgroup(${ Expr(name) }, ${ Expr(prefix) }) }
                case _ => ()
            }
            items.toList
        end markerItems

        // ── Enum ExportType synthesis ─────────────────────────────────────────
        // For a parameterless Scala 3 enum field type, synthesize an ExportType inline
        // (no pre-written given is possible for user-defined enums).
        def enumExportType[A: Type]: Option[Expr[ExportType[A]]] =
            val aSym  = TypeRepr.of[A].typeSymbol
            val cases = aSym.children
            if !aSym.flags.is(Flags.Enum) || cases.isEmpty || cases.exists(_.isClassDef) then None
            else
                val hintStr   = Expr(cases.map(_.name).mkString(","))
                val companion = aSym.companionModule
                Some('{
                    new ExportType[A]:
                        def variantType = VariantType.Int
                        override def hint       = PropertyHint.Enum
                        override def hintString = $hintStr
                        override def usage      = PropertyUsage.Default | PropertyUsage.ClassIsEnum
                        def write(dest: Ptr[Byte], value: A): Unit =
                            Variant.writeInt(
                              dest,
                              value.asInstanceOf[scala.reflect.Enum].ordinal.toLong
                            )
                        def read(src: Ptr[Byte]): A =
                            ${
                                Select.unique(Ref(companion), "fromOrdinal")
                                    .appliedTo('{ Variant.readInt(src).toInt }.asTerm)
                                    .asExprOf[A]
                            }
                })
        end enumExportType

        // Extract the ExportHint argument from a @gdexport annotation, if present.
        // `@gdexport` → ExportHint.none (default); `@gdexport(ExportHint.range(...))` → that hint.
        def exportHintExpr(s: Symbol): Expr[ExportHint] =
            s.getAnnotation(gdexportSym) match
                case Some(Apply(_, arg :: _)) => arg.asExprOf[ExportHint]
                case _                        => '{ ExportHint.none }

        // ── @onready validation ──────────────────────────────────────────────
        sym.declaredFields.filter(_.hasAnnotation(onreadySym)).foreach { f =>
            if !f.flags.is(Flags.Lazy) then
                report.errorAndAbort(s"@onready ${className}.${f.name} must be a lazy val")
        }

        // ── @gdexport on primary constructor var params ───────────────────────
        // Primary constructor `var` params annotated with `@gdexport` are exported before the
        // body fields. Group markers on ctor params are also supported.
        val ctorPropItemExprs: List[Expr[PropertyItem]] = allParams.flatMap { param =>
            val paramMarkers = markerItems(param)
            val paramPropItem: List[Expr[PropertyItem]] =
                if param.hasAnnotation(gdexportSym) && param.flags.is(Flags.Mutable) then
                    val fieldName = param.name
                    val hintExpr  = exportHintExpr(param)
                    param.tree match
                        case vd: ValDef => vd.tpt.tpe.asType match
                                case '[t] =>
                                    val etOpt = Expr.summon[ExportType[t]].orElse(enumExportType[t])
                                    etOpt match
                                        case Some(et) =>
                                            val nameExpr = Expr(fieldName)
                                            val getter: Expr[(GodotObject, Ptr[Byte]) => Unit] =
                                                '{ (obj: GodotObject, ret: Ptr[Byte]) =>
                                                    $et.write(
                                                      ret,
                                                      ${
                                                          Select.unique(
                                                            '{ obj.asInstanceOf[T] }.asTerm,
                                                            fieldName
                                                          ).asExprOf[t]
                                                      }
                                                    )
                                                }
                                            val setter: Expr[(GodotObject, Ptr[Byte]) => Unit] =
                                                '{ (obj: GodotObject, v: Ptr[Byte]) =>
                                                    ${
                                                        Assign(
                                                          Select.unique(
                                                            '{ obj.asInstanceOf[T] }.asTerm,
                                                            fieldName
                                                          ),
                                                          '{ $et.read(v) }.asTerm
                                                        ).asExprOf[Unit]
                                                    }
                                                }
                                            List('{
                                                val h = $hintExpr
                                                val (resolvedHint, resolvedHintStr) =
                                                    if h == ExportHint.none then
                                                        ($et.hint, $et.hintString)
                                                    else
                                                        (h.hint, h.hintString)
                                                PropertyItem.Prop(PropertyDescriptor(
                                                  name = $nameExpr,
                                                  variantType = $et.variantType,
                                                  getter = $getter,
                                                  setter = $setter,
                                                  hint = resolvedHint,
                                                  hintString = resolvedHintStr,
                                                  propClassName = $et.className,
                                                  usage = $et.usage
                                                ))
                                            })
                                        case None =>
                                            report.errorAndAbort(
                                              s"@gdexport $className.${param.name}: no ExportType for " +
                                                  s"type ${vd.tpt.tpe.show}."
                                            )
                        case _ => Nil
                else Nil
            paramMarkers ++ paramPropItem
        }.toList

        // ── @gdexport on body fields + inspector markers ───────────────────────
        // Iterate all declared fields in order. Each field can carry:
        //   - Zero or more inspector section marker annotations → emitted first as marker items
        //   - @gdexport → emitted as a Prop item
        // Fields without either annotation contribute nothing.
        val propItemExprs: List[Expr[PropertyItem]] = sym.declaredFields.flatMap { f =>
            val markers = markerItems(f)
            val propItem: List[Expr[PropertyItem]] =
                if f.hasAnnotation(gdexportSym) && f.flags.is(Flags.Mutable) then
                    val fieldName = f.name
                    val hintExpr  = exportHintExpr(f)
                    f.tree match
                        case fv: ValDef => fv.tpt.tpe.asType match
                                case '[t] =>
                                    val etOpt =
                                        Expr.summon[ExportType[t]].orElse(enumExportType[t])
                                    etOpt match
                                        case Some(et) =>
                                            val nameExpr = Expr(fieldName)
                                            val getter: Expr[(GodotObject, Ptr[Byte]) => Unit] =
                                                '{ (obj: GodotObject, ret: Ptr[Byte]) =>
                                                    $et.write(
                                                      ret,
                                                      ${
                                                          Select.unique(
                                                            '{ obj.asInstanceOf[T] }.asTerm,
                                                            fieldName
                                                          ).asExprOf[t]
                                                      }
                                                    )
                                                }
                                            val setter: Expr[(GodotObject, Ptr[Byte]) => Unit] =
                                                '{ (obj: GodotObject, v: Ptr[Byte]) =>
                                                    ${
                                                        Assign(
                                                          Select.unique(
                                                            '{ obj.asInstanceOf[T] }.asTerm,
                                                            fieldName
                                                          ),
                                                          '{ $et.read(v) }.asTerm
                                                        ).asExprOf[Unit]
                                                    }
                                                }
                                            // If the user supplied an ExportHint, its hint/hintString
                                            // override the ExportType defaults; otherwise fall through.
                                            List('{
                                                val h = $hintExpr
                                                val (resolvedHint, resolvedHintStr) =
                                                    if h == ExportHint.none then
                                                        ($et.hint, $et.hintString)
                                                    else
                                                        (h.hint, h.hintString)
                                                PropertyItem.Prop(PropertyDescriptor(
                                                  name = $nameExpr,
                                                  variantType = $et.variantType,
                                                  getter = $getter,
                                                  setter = $setter,
                                                  hint = resolvedHint,
                                                  hintString = resolvedHintStr,
                                                  propClassName = $et.className,
                                                  usage = $et.usage
                                                ))
                                            })
                                        case None =>
                                            report.errorAndAbort(
                                              s"@gdexport $className.$fieldName: no ExportType for " +
                                                  s"type ${fv.tpt.tpe.show}. " +
                                                  s"Add an ExportType[${fv.tpt.tpe.show}] given, or use " +
                                                  s"a supported type: Boolean, Int, Long, Float, Double, " +
                                                  s"String, Ptr[Vector2/3/Color/...], Gd[T], or a " +
                                                  s"parameterless Scala enum."
                                            )
                        case _ => Nil
                else Nil
            markers ++ propItem
        }.toList

        // ── @signal inner case classes ───────────────────────────────────────
        // For each `@signal case class died(damage: Int, from: String)`, scan its constructor
        // params to build SignalParamInfo entries. Godot uses this to show the signal signature in
        // the editor and to validate typed connections.
        val signalExprs: List[Expr[SignalDescriptor]] = sym.declaredTypes
            .filter(s => s.isClassDef && s.hasAnnotation(signalSym)).map { s =>
                val signalName = Expr(camelToSnake(s.name))
                val ctor       = s.primaryConstructor
                val paramInfoExprs: List[Expr[SignalParamInfo]] =
                    ctor.paramSymss.flatten.filterNot(_.flags.is(Flags.Given)).flatMap { param =>
                        param.tree match
                            case vd: ValDef => vd.tpt.tpe.asType match
                                    case '[pt] =>
                                        val pName = Expr(param.name)
                                        // Prefer ExportType (has className for Object params), fall back to ToVariant.
                                        val infoExpr: Expr[SignalParamInfo] =
                                            Expr.summon[ExportType[pt]] match
                                                case Some(et) =>
                                                    '{ SignalParamInfo($pName, $et.variantType, $et.className) }
                                                case None => Expr.summon[ToVariant[pt]] match
                                                        case Some(tv) =>
                                                            '{ SignalParamInfo($pName, $tv.variantType) }
                                                        case None =>
                                                            report.errorAndAbort(
                                                              s"@signal ${s.name}: no ToVariant for param " +
                                                                  s"'${param.name}: ${vd.tpt.tpe.show}'. " +
                                                                  s"Supported: Boolean, Int, Long, Float, Double, " +
                                                                  s"String, Ptr[Vector2/...] (import gdext.generated.*), Gd[T]."
                                                            )
                                        List(infoExpr)
                            case _ => Nil
                    }.toList
                '{ SignalDescriptor($signalName, ${ Expr.ofList(paramInfoExprs) }) }
            }.toList

        // ── @func methods ────────────────────────────────────────────────────

        def methodParamDefs(m: Symbol): List[ValDef] = m.tree match
            case d: DefDef => d.termParamss.flatMap(_.params)
                    .filterNot(_.symbol.flags.is(Flags.Given))
            case _ => Nil

        def checkParams(m: Symbol): Unit = methodParamDefs(m).foreach { vd =>
            vd.tpt.tpe.asType match
                case '[pt] => if Expr.summon[FromVariant[pt]].isEmpty then
                        report.errorAndAbort(
                          s"@func $className.${m.name}: no FromVariant for " +
                              s"param '${vd.name}: ${vd.tpt.tpe.show}'. " +
                              s"Supported: Boolean, Int, Long, Float, Double, String, " +
                              s"Ptr[Vector2/3/Color/...] (import gdext.generated.*), Gd[T]."
                        )
                case _ => ()
        }

        val methodExprs: List[Expr[MethodEntry]] = sym.declaredMethods
            .filterNot(_.flags.is(Flags.Synthetic)).filter(_.hasAnnotation(funcSym)).map { m =>
                checkParams(m)
                val paramDefs = methodParamDefs(m)
                val retTpe    = m.tree match
                    case d: DefDef => d.returnTpt.tpe
                    case _         => TypeRepr.of[Unit]
                val godotName = Expr(camelToSnake(m.name))
                val argCount  = Expr(paramDefs.size)

                def buildArgReads(argsE: Expr[Ptr[Ptr[Byte]]]): List[Term] = paramDefs.zipWithIndex
                    .map { (vd, i) =>
                        vd.tpt.tpe.asType match
                            case '[pt] =>
                                val fv = Expr.summon[FromVariant[pt]].get
                                '{ $fv.read($argsE(${ Expr(i) })) }.asTerm
                    }.toList

                if retTpe =:= TypeRepr.of[Unit] then
                    val dispatch: Expr[(GodotObject, Ptr[Ptr[Byte]], Long, Ptr[Byte]) => Unit] = '{
                        (callObj: GodotObject, callArgs: Ptr[Ptr[Byte]], _c: Long, _r: Ptr[Byte]) =>
                            ${
                                Select.unique('{ callObj.asInstanceOf[T] }.asTerm, m.name)
                                    .appliedToArgs(buildArgReads('callArgs)).asExprOf[Unit]
                            }
                    }
                    '{ MethodEntry($godotName, $dispatch, argumentCount = $argCount) }
                else
                    retTpe.asType match
                        case '[r] => Expr.summon[ToVariant[r]] match
                                case Some(tv) =>
                                    val dispatch: Expr[
                                      (GodotObject, Ptr[Ptr[Byte]], Long, Ptr[Byte]) => Unit
                                    ] = '{
                                        (
                                            callObj: GodotObject,
                                            callArgs: Ptr[Ptr[Byte]],
                                            _c: Long,
                                            ret: Ptr[Byte]
                                        ) =>
                                            $tv.write(
                                              ret,
                                              ${
                                                  Select.unique(
                                                    '{ callObj.asInstanceOf[T] }.asTerm,
                                                    m.name
                                                  ).appliedToArgs(buildArgReads('callArgs))
                                                      .asExprOf[r]
                                              }
                                            )
                                    }
                                    '{
                                        MethodEntry(
                                          $godotName,
                                          $dispatch,
                                          hasReturnValue = true,
                                          returnVariantType = $tv.variantType,
                                          argumentCount = $argCount
                                        )
                                    }
                                case None =>
                                    report.errorAndAbort(
                                      s"@func $className.${m.name}: no ToVariant for return " +
                                          s"type '${retTpe.show}'. " +
                                          s"Supported: Boolean, Int, Long, Float, Double, String, " +
                                          s"Ptr[Vector2/3/Color/...] (import gdext.generated.*), Gd[T]."
                                    )
            }.toList

        // ── Emit GdClassRegistry.register ────────────────────────────────────
        val allPropItemExprs = ctorPropItemExprs ++ propItemExprs
        '{
            GdClassRegistry.register(
              ${ Expr(className) },
              ${ Expr(baseName) },
              $factoryExpr,
              $filteredVirtualsExpr,
              properties = ${ Expr.ofList(allPropItemExprs) },
              methods = ${ Expr.ofList(methodExprs) },
              signals = ${ Expr.ofList(signalExprs) },
              isRuntime = ${ Expr(isRuntime) }
            )
        }
    end autoImpl
end Register
