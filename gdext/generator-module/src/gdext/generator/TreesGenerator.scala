package gdext.generator

import scala.meta.*

object TreesGenerator:
    given Dialect = dialects.Scala3

    // ── String utilities ──────────────────────────────────────────────────────

    val godotClassVirtuals: Map[String, (String, Int)] =
        Map("_ready" -> ("Unit", 0), "_process" -> ("Unit", 1), "_physicsProcess" -> ("Unit", 1))

    val jvmMethodConflicts: Set[String] =
        Set("wait", "notify", "notifyAll", "toString", "hashCode", "finalize", "getClass")

    def toCamel(name: String): String =
        val leading = name.takeWhile(_ == '_')
        val parts   = name.dropWhile(_ == '_').split("_")
        leading + parts.zipWithIndex.map { (p, i) =>
            if i == 0 || p.isEmpty then p else p.head.toUpper.toString + p.tail
        }.mkString
    end toCamel

    def isPrimitiveMeta(meta: String): Boolean = meta match
        case "float" | "int32" | "int64" | "double" => true
        case _                                      => false

    def metaToScalaType(meta: String): String = meta match
        case "float"  => "Float"
        case "int32"  => "Int"
        case "int64"  => "Long"
        case "double" => "Double"
        case other    => other

    // ── Common tree helpers ───────────────────────────────────────────────────

    private val ptrByte    = Type.Apply(Type.Name("Ptr"), List(Type.Name("Byte")))
    private val ptrLong    = Type.Apply(Type.Name("Ptr"), List(Type.Name("Long")))
    private val ptrDouble  = Type.Apply(Type.Name("Ptr"), List(Type.Name("Double")))
    private val ptrPtrByte = Type.Apply(Type.Name("Ptr"), List(ptrByte))

    private def cStr(s: String): Term = Term.Interpolate(Term.Name("c"), List(Lit.String(s)), Nil)

    private def stackallocTerm(tp: Type, size: Option[Int] = None): Term = Term.Apply(
      Term.ApplyType(Term.Name("stackalloc"), List(tp)),
      size.fold(Nil)(n => List(Lit.Int(n)))
    )

    private def asInstanceOfTerm(t: Term, tp: Type): Term = Term
        .ApplyType(Term.Select(t, Term.Name("asInstanceOf")), List(tp))

    private def derefAssign(lhs: Term, rhs: Term): Stat = Term
        .Assign(Term.ApplyUnary(Term.Name("!"), lhs), rhs)

    private def simpleValDef(name: String, rhs: Term): Stat = Defn
        .Val(Nil, List(Pat.Var(Term.Name(name))), None, rhs)

    private def lazyValDef(name: String, tpe: Type, rhs: Term): Stat = Defn
        .Val(List(Mod.Lazy()), List(Pat.Var(Term.Name(name))), Some(tpe), rhs)

    private def zoneWrap(body: List[Stat]): Term = Term
        .Apply(Term.Name("Zone"), List(Term.Block(body)))

    private def emptyTemplate(stats: List[Stat]): Template =
        Template(Nil, Nil, Self(Name.Anonymous(), None), stats)

    private def simpleObject(name: String, stats: List[Stat]): Defn.Object = Defn
        .Object(Nil, Term.Name(name), emptyTemplate(stats))

    private def buildInlineDef(
        name: String,
        paramss: List[List[Term.Param]],
        retType: Type,
        body: Term
    ): Defn.Def = Defn.Def(List(Mod.Inline()), Term.Name(name), Nil, paramss, Some(retType), body)

    private def buildExtensionGroup(
        extParam: Term.Param,
        usingParams: List[Term.Param] = Nil,
        methods: List[Defn.Def]
    ): Defn.ExtensionGroup =
        val paramss = List(List(extParam)) ++
            (if usingParams.nonEmpty then List(usingParams) else Nil)
        Defn.ExtensionGroup(Nil, paramss, Term.Block(methods))
    end buildExtensionGroup

    private def buildGivenAlias(tpe: Type, rhs: Term): Defn.GivenAlias = Defn
        .GivenAlias(Nil, Name.Anonymous(), Nil, Nil, tpe, rhs)

    private def buildVarDef(name: String, tpe: Type, rhs: Term): Defn.Var = Defn
        .Var(Nil, List(Pat.Var(Term.Name(name))), Some(tpe), Some(rhs))

    private def buildUsingParam(name: String, tpe: Type): Term.Param = Term
        .Param(List(Mod.Using()), Term.Name(name), Some(tpe), None)

    // ── Type mapping ──────────────────────────────────────────────────────────

    def godotTypeStr(
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
        case t if valueBuiltins.contains(t)    => s"Ptr[$t]"
        case t                                 => t

    private def paramGodotTypeStr(
        godotType: String,
        meta: Option[String],
        valueBuiltins: Set[String] = Set.empty
    ): String = godotType match
        case "String" | "StringName" => "String"
        case other                   => godotTypeStr(other, meta, valueBuiltins)

    private def godotType(t: String, meta: Option[String], vb: Set[String] = Set.empty): Type =
        t match
            case "void"  => Type.Name("Unit")
            case "bool"  => Type.Name("Boolean")
            case "int"   => Type.Name(if meta.exists(_.contains("32")) then "Int" else "Long")
            case "float" => Type.Name(if meta.contains("float") then "Float" else "Double")
            case "String" | "StringName"                                   => Type.Name("CString")
            case t if t.startsWith("typedarray::")                         => ptrByte
            case t if t.startsWith("enum::") || t.startsWith("bitfield::") => Type.Name("Int")
            case "Variant" | "void*" | "Array"                             => ptrByte
            case t if t.endsWith("*")                                      => ptrByte
            case t if vb.contains(t) => Type.Apply(Type.Name("Ptr"), List(Type.Name(t)))
            case t                   => Type.Name(t)

    private def paramGodotType(t: String, meta: Option[String], vb: Set[String] = Set.empty): Type =
        t match
            case "String" | "StringName" => Type.Name("String")
            case other                   => godotType(other, meta, vb)

    // ── Low-level statement builders ──────────────────────────────────────────

    private def packArg(
        arg: Ast.GodotArg,
        i: Int,
        vb: Set[String] = Set.empty
    ): (List[Stat], Term) =
        val param = Term.Name(toCamel(arg.name))
        val slotN = s"_a$i"
        val slot  = Term.Name(slotN)

        def stackAndAssign(tp: Type, rhs: Term): (List[Stat], Term) =
            val setup = List(simpleValDef(slotN, stackallocTerm(tp)), derefAssign(slot, rhs))
            (setup, asInstanceOfTerm(slot, ptrByte))

        arg.typeName match
            case "bool" =>
                val boolTerm = Term.If(
                  param,
                  Term.Select(Lit.Int(1), Term.Name("toByte")),
                  Term.Select(Lit.Int(0), Term.Name("toByte"))
                )
                stackAndAssign(Type.Name("Byte"), boolTerm)
            case "int" =>
                val rhs =
                    if arg.meta.exists(_.contains("32")) then
                        Term.Select(param, Term.Name("toLong"))
                    else param
                stackAndAssign(Type.Name("Long"), rhs)
            case "float" =>
                val rhs =
                    if arg.meta.contains("float") then Term.Select(param, Term.Name("toDouble"))
                    else param
                stackAndAssign(Type.Name("Double"), rhs)
            case t if t.startsWith("enum::") || t.startsWith("bitfield::") =>
                stackAndAssign(Type.Name("Long"), Term.Select(param, Term.Name("toLong")))
            case "String" =>
                val alloc = simpleValDef(slotN, stackallocTerm(Type.Name("Byte"), Some(8)))
                val init  = Term.Apply(
                  Term.Select(Term.Name("GdxApi"), Term.Name("initGodotString")),
                  List(slot, Term.Apply(Term.Name("toCString"), List(param)))
                )
                (List(alloc, init), slot)
            case "StringName" =>
                val alloc = simpleValDef(slotN, stackallocTerm(Type.Name("Byte"), Some(8)))
                val init  = Term.Apply(
                  Term.Select(Term.Name("GdxApi"), Term.Name("initStringName")),
                  List(slot, Term.Apply(Term.Name("toCString"), List(param)))
                )
                (List(alloc, init), slot)
            case "Variant" | "void*" | "Array"     => (Nil, param)
            case t if t.startsWith("typedarray::") => (Nil, param)
            case t if t.endsWith("*")              => (Nil, param)
            case t if vb.contains(t)               => (Nil, asInstanceOfTerm(param, ptrByte))
            case _ => stackAndAssign(ptrByte, Term.Select(param, Term.Name("ptr")))
        end match
    end packArg

    private def packDefaultArg(
        arg: Ast.GodotArg,
        i: Int,
        vb: Set[String] = Set.empty
    ): (List[Stat], Term) =
        val slotN   = s"_a$i"
        val slot    = Term.Name(slotN)
        val default = arg.defaultValue.getOrElse("null")

        def stackAndAssign(tp: Type, rhs: Term): (List[Stat], Term) =
            val setup = List(simpleValDef(slotN, stackallocTerm(tp)), derefAssign(slot, rhs))
            (setup, asInstanceOfTerm(slot, ptrByte))

        arg.typeName match
            case "bool" =>
                val bVal =
                    if default == "true" then Term.Select(Lit.Int(1), Term.Name("toByte"))
                    else Term.Select(Lit.Int(0), Term.Name("toByte"))
                stackAndAssign(Type.Name("Byte"), bVal)
            case "int" => stackAndAssign(
                  Type.Name("Long"),
                  Lit.Long(scala.util.Try(default.toLong).getOrElse(0L))
                )
            case t if t.startsWith("enum::") || t.startsWith("bitfield::") =>
                stackAndAssign(
                  Type.Name("Long"),
                  Lit.Long(scala.util.Try(default.toLong).getOrElse(0L))
                )
            case "float" => stackAndAssign(
                  Type.Name("Double"),
                  Lit.Double(scala.util.Try(default.toDouble).getOrElse(0.0))
                )
            case "String" =>
                val alloc = simpleValDef(slotN, stackallocTerm(Type.Name("Byte"), Some(8)))
                val init  = Term.Apply(
                  Term.Select(Term.Name("GdxApi"), Term.Name("initGodotString")),
                  List(slot, cStr(""))
                )
                (List(alloc, init), slot)
            case "StringName" =>
                val alloc = simpleValDef(slotN, stackallocTerm(Type.Name("Byte"), Some(8)))
                val init  = Term.Apply(
                  Term.Select(Term.Name("GdxApi"), Term.Name("initStringName")),
                  List(slot, cStr(""))
                )
                (List(alloc, init), slot)
            case _ if default == "null" => stackAndAssign(ptrByte, Lit.Null())
            case _ => (List(simpleValDef(slotN, stackallocTerm(Type.Name("Byte"), Some(24)))), slot)
        end match
    end packDefaultArg

    private def retSetup(
        godotType: String,
        meta: Option[String],
        vb: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty
    ): (Option[Stat], Option[Term]) = godotType match
        case "void" => (None, None)
        case "bool" =>
            val alloc = simpleValDef("_ret", stackallocTerm(Type.Name("Byte")))
            val read  = Term.ApplyInfix(
              Term.ApplyUnary(Term.Name("!"), Term.Name("_ret")),
              Term.Name("!="),
              Nil,
              List(Term.Select(Lit.Int(0), Term.Name("toByte")))
            )
            (Some(alloc), Some(read))
        case "int" =>
            val alloc = simpleValDef("_ret", stackallocTerm(Type.Name("Long")))
            val read  =
                if meta.exists(_.contains("32")) then
                    Term.Select(
                      Term.ApplyUnary(Term.Name("!"), Term.Name("_ret")),
                      Term.Name("toInt")
                    )
                else Term.ApplyUnary(Term.Name("!"), Term.Name("_ret"))
            (Some(alloc), Some(read))
        case "float" =>
            val alloc = simpleValDef("_ret", stackallocTerm(Type.Name("Double")))
            val read  =
                if meta.contains("float") then
                    Term.Select(
                      Term.ApplyUnary(Term.Name("!"), Term.Name("_ret")),
                      Term.Name("toFloat")
                    )
                else Term.ApplyUnary(Term.Name("!"), Term.Name("_ret"))
            (Some(alloc), Some(read))
        case t if t.startsWith("enum::") || t.startsWith("bitfield::") =>
            val alloc = simpleValDef("_ret", stackallocTerm(Type.Name("Long")))
            val read  = Term
                .Select(Term.ApplyUnary(Term.Name("!"), Term.Name("_ret")), Term.Name("toInt"))
            (Some(alloc), Some(read))
        case t if vb.contains(t) =>
            val ptrT  = Type.Apply(Type.Name("Ptr"), List(Type.Name(t)))
            val alloc = simpleValDef(
              "_ret",
              asInstanceOfTerm(
                Term.Apply(
                  Term.Name("malloc"),
                  List(Term.ApplyType(Term.Name("sizeof"), List(Type.Name(t))))
                ),
                ptrT
              )
            )
            (Some(alloc), Some(Term.Name("_ret")))
        case t =>
            val alloc      = simpleValDef("_ret", stackallocTerm(ptrByte))
            val read: Term = t match
                case "String" | "StringName" | "Variant" | "void*" | "Array" => Term
                        .ApplyUnary(Term.Name("!"), Term.Name("_ret"))
                case t2 if t2.startsWith("typedarray::") =>
                    Term.ApplyUnary(Term.Name("!"), Term.Name("_ret"))
                case t2 if refcountedTypes.contains(t2) =>
                    Term.Block(List(
                      simpleValDef(
                        "_r",
                        Term.New(Init(
                          Type.Name(t2),
                          Name.Anonymous(),
                          List(List(Term.ApplyUnary(Term.Name("!"), Term.Name("_ret"))))
                        ))
                      ),
                      Term.If(
                        Term.ApplyInfix(
                          Term.Select(Term.Name("_r"), Term.Name("ptr")),
                          Term.Name("!="),
                          Nil,
                          List(Lit.Null())
                        ),
                        Term.Apply(Term.Select(Term.Name("_r"), Term.Name("reference")), Nil),
                        Lit.Unit()
                      ),
                      Term.Name("_r")
                    ))
                case t2 => Term.New(Init(
                      Type.Name(t2),
                      Name.Anonymous(),
                      List(List(Term.ApplyUnary(Term.Name("!"), Term.Name("_ret"))))
                    ))
            (Some(alloc), Some(read))
    end retSetup

    // ── Tree assembly helpers ─────────────────────────────────────────────────

    private def setupArgStats(
        args: Vector[Ast.GodotArg],
        vb: Set[String],
        isVararg: Boolean = false
    ): List[Stat] =
        if isVararg then List(simpleValDef("_args", Term.Name("args")))
        else if args.isEmpty then
            List(simpleValDef("_args", asInstanceOfTerm(Lit.Null(), ptrPtrByte)))
        else
            val reqArgs   = args.filterNot(_.hasDefault)
            val optArgs   = args.filter(_.hasDefault)
            val reqPacked = reqArgs.zipWithIndex.map((a, i) => packArg(a, i, vb))
            val optPacked = optArgs.zipWithIndex
                .map((a, i) => packDefaultArg(a, reqArgs.size + i, vb))
            val argsAlloc = simpleValDef("_args", stackallocTerm(ptrByte, Some(args.size)))
            val assigns = (reqPacked ++ optPacked).zipWithIndex.flatMap { case ((setup, expr), i) =>
                val assign: Stat = Term
                    .Assign(Term.Apply(Term.Name("_args"), List(Lit.Int(i))), expr)
                setup :+ assign
            }
            argsAlloc :: assigns.toList

    private def godotParam(a: Ast.GodotArg, vb: Set[String]): Term.Param = Term
        .Param(Nil, Term.Name(toCamel(a.name)), Some(paramGodotType(a.typeName, a.meta, vb)), None)

    // ── Member builders ───────────────────────────────────────────────────────

    def buildDispatchLambda(
        m: Ast.GodotMethod,
        definingClass: String,
        valueBuiltins: Set[String]
    ): Term =
        val camelName = toCamel(m.name)
        val _obj      = Term.Name("_obj")
        val _args     = Term.Name("_args")
        val _ret      = Term.Name("_ret")

        val argReads: List[Stat] = m.args.zipWithIndex.map { (a, i) =>
            val argI       = Term.Apply(_args, List(Lit.Int(i)))
            val read: Term = a.typeName match
                case "bool" => Term.ApplyInfix(
                      Term.ApplyUnary(
                        Term.Name("!"),
                        asInstanceOfTerm(
                          argI,
                          Type.Apply(Type.Name("Ptr"), List(Type.Name("Byte")))
                        )
                      ),
                      Term.Name("!="),
                      Nil,
                      List(Lit.Int(0))
                    )
                case "int" =>
                    val deref = Term.ApplyUnary(Term.Name("!"), asInstanceOfTerm(argI, ptrLong))
                    if a.meta.exists(_.contains("32")) then Term.Select(deref, Term.Name("toInt"))
                    else deref
                case "float" =>
                    val deref = Term.ApplyUnary(Term.Name("!"), asInstanceOfTerm(argI, ptrDouble))
                    if a.meta.contains("float") then Term.Select(deref, Term.Name("toFloat"))
                    else deref
                case "String" | "StringName" => asInstanceOfTerm(Lit.Null(), Type.Name("String"))
                case t if t.startsWith("enum::") || t.startsWith("bitfield::") =>
                    Term.Select(
                      Term.ApplyUnary(Term.Name("!"), asInstanceOfTerm(argI, ptrLong)),
                      Term.Name("toInt")
                    )
                case t if valueBuiltins.contains(t) =>
                    asInstanceOfTerm(argI, Type.Apply(Type.Name("Ptr"), List(Type.Name(t))))
                case "Variant" | "void*" | "Array"     => argI
                case t if t.startsWith("typedarray::") => argI
                case t if t.endsWith("*")              => argI
                case t                                 => Term.New(Init(
                      Type.Name(t),
                      Name.Anonymous(),
                      List(
                        List(Term.ApplyUnary(Term.Name("!"), asInstanceOfTerm(argI, ptrPtrByte)))
                      )
                    ))
            simpleValDef(s"_v$i", read)
        }.toList

        val argVars  = m.args.indices.map(i => Term.Name(s"_v$i")).toList
        val callExpr = Term.Apply(
          Term.Select(asInstanceOfTerm(_obj, Type.Name(definingClass)), Term.Name(camelName)),
          argVars
        )

        val retLines: List[Stat] = m.returnTypeName match
            case "void" => List(callExpr)
            case "bool" => List(
                  simpleValDef("_r", callExpr),
                  derefAssign(
                    asInstanceOfTerm(_ret, Type.Apply(Type.Name("Ptr"), List(Type.Name("Byte")))),
                    Term.If(
                      Term.Name("_r"),
                      Term.Select(Lit.Int(1), Term.Name("toByte")),
                      Term.Select(Lit.Int(0), Term.Name("toByte"))
                    )
                  )
                )
            case "int" if m.returnMeta.exists(_.contains("32")) =>
                List(
                  simpleValDef("_r", callExpr),
                  derefAssign(
                    asInstanceOfTerm(_ret, ptrLong),
                    Term.Select(Term.Name("_r"), Term.Name("toLong"))
                  )
                )
            case "int" => List(
                  simpleValDef("_r", callExpr),
                  derefAssign(asInstanceOfTerm(_ret, ptrLong), Term.Name("_r"))
                )
            case "float" if m.returnMeta.contains("float") =>
                List(
                  simpleValDef("_r", callExpr),
                  derefAssign(
                    asInstanceOfTerm(_ret, ptrDouble),
                    Term.Select(Term.Name("_r"), Term.Name("toDouble"))
                  )
                )
            case "float" => List(
                  simpleValDef("_r", callExpr),
                  derefAssign(asInstanceOfTerm(_ret, ptrDouble), Term.Name("_r"))
                )
            case t if t.startsWith("enum::") || t.startsWith("bitfield::") =>
                List(
                  simpleValDef("_r", callExpr),
                  derefAssign(
                    asInstanceOfTerm(_ret, ptrLong),
                    Term.Select(Term.Name("_r"), Term.Name("toLong"))
                  )
                )
            case t
                if t == "String" || t == "StringName" || t == "PackedStringArray" || t == "Array" ||
                    t == "Variant" || t == "void*" || t.startsWith("typedarray::") ||
                    t.endsWith("*") || valueBuiltins.contains(t) => List(callExpr)
            case _ => List(
                  simpleValDef("_r", callExpr),
                  derefAssign(
                    asInstanceOfTerm(_ret, ptrPtrByte),
                    Term.If(
                      Term.ApplyInfix(Term.Name("_r"), Term.Name("!="), Nil, List(Lit.Null())),
                      Term.Select(Term.Name("_r"), Term.Name("ptr")),
                      Lit.Null()
                    )
                  )
                )

        val lambdaParams = List("_obj", "_args", "_ret")
            .map(n => Term.Param(Nil, Term.Name(n), None, None))
        Term.Function(lambdaParams, Term.Block(argReads ++ retLines))
    end buildDispatchLambda

    private def methodBody(
        cls: Ast.GodotClass,
        m: Ast.GodotMethod,
        isStatic: Boolean,
        valueBuiltins: Set[String],
        refcountedTypes: Set[String]
    ): Term =
        val name            = toCamel(m.name)
        val (rAlloc, rRead) =
            retSetup(m.returnTypeName, m.returnMeta, valueBuiltins, refcountedTypes)
        val retPtr: Term =
            if rAlloc.isDefined then asInstanceOfTerm(Term.Name("_ret"), ptrByte) else Lit.Null()
        val selfArg: Term  = if isStatic then Lit.Null() else Term.Name("ptr")
        val callStat: Stat = Term.Apply(
          Term.Select(Term.Name("GdxApi"), Term.Name("ptrcall")),
          List(
            Term.Select(Term.Select(Term.Name(cls.name), Term.Name("Binds")), Term.Name(name)),
            selfArg,
            Term.Name("_args"),
            retPtr
          )
        )
        val body = setupArgStats(m.args, valueBuiltins) ++ rAlloc.toList ++ List(callStat) ++
            rRead.toList
        val needsZone = m.args.filterNot(_.hasDefault)
            .exists(a => a.typeName == "String" || a.typeName == "StringName")
        if needsZone then zoneWrap(body) else Term.Block(body)
    end methodBody

    def buildMethod(
        cls: Ast.GodotClass,
        m: Ast.GodotMethod,
        isStatic: Boolean = false,
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty
    ): Defn.Def =
        val name    = toCamel(m.name)
        val params  = m.args.filterNot(_.hasDefault).map(godotParam(_, valueBuiltins)).toList
        val retType = godotType(m.returnTypeName, m.returnMeta, valueBuiltins)
        val body    = methodBody(cls, m, isStatic, valueBuiltins, refcountedTypes)
        Defn.Def(Nil, Term.Name(name), Nil, List(params), Some(retType), body)
    end buildMethod

    def buildVirtualStub(
        m: Ast.GodotMethod,
        valueBuiltins: Set[String] = Set.empty
    ): Option[Defn.Def] =
        val camelName  = toCamel(m.name)
        val retTypeStr = godotTypeStr(m.returnTypeName, m.returnMeta, valueBuiltins)
        val arity      = m.args.length
        val mods       = godotClassVirtuals.get(camelName) match
            case Some((expectedRet, expectedArity))
                if expectedRet == retTypeStr && expectedArity == arity => List(Mod.Override())
            case Some((expectedRet, _)) if expectedRet != retTypeStr => return None
            case _                                                   => Nil
        val params            = m.args.map(godotParam(_, valueBuiltins)).toList
        val retType           = godotType(m.returnTypeName, m.returnMeta, valueBuiltins)
        val defaultBody: Term = m.returnTypeName match
            case "void"                                                    => Lit.Unit()
            case "bool"                                                    => Lit.Boolean(false)
            case "int" | "float"                                           => Lit.Int(0)
            case t if t.startsWith("enum::") || t.startsWith("bitfield::") => Lit.Int(0)
            case _                                                         => Lit.Null()
        Some(Defn.Def(mods, Term.Name(camelName), Nil, List(params), Some(retType), defaultBody))
    end buildVirtualStub

    def buildForwardingMethod(
        cls: Ast.GodotClass,
        m: Ast.GodotMethod,
        valueBuiltins: Set[String] = Set.empty
    ): Defn.Def =
        val name     = toCamel(m.name)
        val reqArgs  = m.args.filterNot(_.hasDefault)
        val params   = reqArgs.map(godotParam(_, valueBuiltins)).toList
        val retType  = godotType(m.returnTypeName, m.returnMeta, valueBuiltins)
        val callArgs = reqArgs.map(a => Term.Name(toCamel(a.name))).toList
        val body     = Term.Apply(Term.Select(Term.Name("singleton"), Term.Name(name)), callArgs)
        Defn.Def(Nil, Term.Name(name), Nil, List(params), Some(retType), body)
    end buildForwardingMethod

    // ── File builders ─────────────────────────────────────────────────────────

    private def pkgStat(pkgPath: String): Stat =
        val parts = pkgPath.split("\\.")
        val ref   = parts.tail.foldLeft[Term.Ref](Term.Name(parts.head)) { (acc, p) =>
            Term.Select(acc, Term.Name(p))
        }
        Pkg(ref.asInstanceOf[Term.Ref], Nil)
    end pkgStat

    private def importStat(imp: String): Stat = imp.parse[Stat].get

    private def buildSource(pkg: String, imports: List[String], stats: List[Stat]): String =
        val pkgRef = pkgStat(pkg) match
            case Pkg(ref, _) => ref
            case s           => Term.Name(pkg)
        Source(List(Pkg(pkgRef, imports.map(importStat) ++ stats))).syntax
    end buildSource

    def functionDefStat(name: String, function: Ast.Kind.Function): Stat =
        val Ast.Kind.Function(arguments, returnValue) = function
        val argTypes                                  = arguments
            .map(a => Parser.typeName(a.typeDescription.typeName).parse[Type].get)
        val retType = returnValue.map(r => Parser.typeName(r.typeName).parse[Type].get)
            .getOrElse(Type.Name("Unit"))
        val cfuncPtrType = Type
            .Apply(Type.Name(s"CFuncPtr${arguments.length}"), (argTypes :+ retType).toList)
        Defn.Type(Nil, Type.Name(name), Nil, cfuncPtrType, Type.Bounds(None, None))
    end functionDefStat

    def functionDefinitionStr(comment: String, name: String, function: Ast.Kind.Function): String =
        val stat = functionDefStat(name, function)
        if comment.nonEmpty then s"$comment\n${stat.syntax}\n" else s"${stat.syntax}\n"

    // ── Types file ────────────────────────────────────────────────────────────

    def typesSource(kindName: String, types: Vector[Ast.Type]): String =
        def withComment(comment: String, stat: Stat): Stat =
            if comment.nonEmpty then s"$comment\n${stat.syntax}".parse[Stat].get else stat

        val stats: List[Stat] = types.flatMap { tp =>
            val comment = util.formatComment(tp)
            tp.kind match
                case Ast.Kind.Alias(underlying) =>
                    val rhs = Parser.typeName(underlying).parse[Type].get
                    List(withComment(
                      comment,
                      Defn.Type(Nil, Type.Name(tp.name), Nil, rhs, Type.Bounds(None, None))
                    ))
                case Ast.Kind.Handle(_, _, _) => List(withComment(
                      comment,
                      Defn.Type(Nil, Type.Name(tp.name), Nil, ptrByte, Type.Bounds(None, None))
                    ))
                case Ast.Kind.Enum(values, isBitfield) =>
                    val baseType = if isBitfield then "CInt" else "CUnsignedInt"
                    val typeDef  = Defn.Type(
                      Nil,
                      Type.Name(tp.name),
                      Nil,
                      Type.Name(baseType),
                      Type.Bounds(None, None)
                    )
                    val valStats: List[Stat] = values.sortBy(_.index).map { v =>
                        val rhs: Term =
                            if isBitfield then Lit.Int(v.index)
                            else Term.Select(Lit.Int(v.index), Term.Name("toUInt"))
                        Defn.Val(
                          List(Mod.Final()),
                          List(Pat.Var(Term.Name(v.name))),
                          Some(Type.Name(tp.name)),
                          rhs
                        )
                    }.toList
                    List(withComment(comment, typeDef), simpleObject(tp.name, valStats))
                case Ast.Kind.Struct(_) => List(withComment(
                      comment,
                      Defn.Type(Nil, Type.Name(tp.name), Nil, ptrByte, Type.Bounds(None, None))
                    ))
                case Ast.Kind.Function(args, ret) => List(
                      withComment(comment, functionDefStat(tp.name, Ast.Kind.Function(args, ret)))
                    )
            end match
        }.toList

        "// Generated by gdext generator — do not edit.\n" + buildSource(
          "gdext.generated.types",
          List(
            "import scala.scalanative.unsafe.*",
            "import scala.scalanative.unsigned.*",
            "import scala.scalanative.unsigned.UInt.*"
          ),
          stats
        )
    end typesSource

    // ── Interface file ────────────────────────────────────────────────────────

    def interfaceSource(interfaces: Vector[Ast.Interface]): String =
        def getInterfaceName(fromName: String) =
            s"GDExtensionInterface${fromName.split("_").map(_.capitalize).mkString}"

        val typeDefs: List[Stat] = interfaces.map { iface =>
            val comment  = util.formatComment(iface.description, iface.deprecated)
            val typeStat = functionDefStat(
              getInterfaceName(iface.name),
              Ast.Kind.Function(iface.arguments, iface.returnValue)
            )
            if comment.nonEmpty then s"$comment\n${typeStat.syntax}".parse[Stat].get else typeStat
        }.toList

        val varFields: List[Stat] = interfaces.map { iface =>
            val tn = getInterfaceName(iface.name)
            buildVarDef(iface.name, Type.Name(tn), asInstanceOfTerm(Lit.Null(), Type.Name(tn)))
        }.toList

        val interfaceClass = Defn.Class(
          Nil,
          Type.Name("Interface"),
          Nil,
          Ctor.Primary(List(Mod.Private(Name.Anonymous())), Name.Anonymous(), List(Nil)),
          emptyTemplate(varFields)
        )

        val batchSize = 20
        val batches   = interfaces.grouped(batchSize).toVector

        val resultParam = Term.Param(Nil, Term.Name("result"), Some(Type.Name("Interface")), None)
        val getProcAddrParam = Term.Param(
          Nil,
          Term.Name("getProcAddr"),
          Some(Type.Name("GDExtensionInterfaceGetProcAddress")),
          None
        )
        val zoneParam = buildUsingParam("zone", Type.Name("Zone"))

        val helperMethods: List[Defn.Def] = batches.zipWithIndex.map { (batch, idx) =>
            val bodyStats: List[Stat] = batch.map { iface =>
                val tn = getInterfaceName(iface.name)
                Term.Assign(
                  Term.Select(Term.Name("result"), Term.Name(iface.name)),
                  asInstanceOfTerm(
                    Term.Apply(
                      Term.Select(Term.Name("getProcAddr"), Term.Name("apply")),
                      List(Term.Apply(Term.Name("toCString"), List(Lit.String(iface.name))))
                    ),
                    Type.Name(tn)
                  )
                )
            }.toList
            Defn.Def(
              List(Mod.Private(Name.Anonymous())),
              Term.Name(s"loadBatch$idx"),
              Nil,
              List(List(resultParam, getProcAddrParam), List(zoneParam)),
              Some(Type.Name("Unit")),
              Term.Block(bodyStats)
            )
        }.toList

        val loadCalls: List[Stat] = batches.indices.map { i =>
            Term.Apply(
              Term.Name(s"loadBatch$i"),
              List(Term.Name("result"), Term.Name("getProcAddr"))
            )
        }.toList

        val givenZone = buildGivenAlias(Type.Name("Zone"), Term.Name("zone"))
        val newResult = simpleValDef(
          "result",
          Term.New(Init(Type.Name("Interface"), Name.Anonymous(), List(Nil)))
        )
        val acquireLambda = Term.Function(
          List(Term.Param(Nil, Term.Name("zone"), Some(Type.Name("Zone")), None)),
          Term.Block(List(givenZone, newResult) ++ loadCalls :+ Term.Name("result"))
        )
        val loadMethod = Defn.Def(
          Nil,
          Term.Name("load"),
          Nil,
          List(List(Term.Param(
            Nil,
            Term.Name("getProcAddr"),
            Some(Type.Name("GDExtensionInterfaceGetProcAddress")),
            None
          ))),
          Some(Type.Name("Interface")),
          Term.Apply(Term.Select(Term.Name("Zone"), Term.Name("acquire")), List(acquireLambda))
        )

        val companionObj = simpleObject("Interface", helperMethods :+ loadMethod)

        "// Generated by gdext generator — do not edit.\n" + buildSource(
          "gdext.generated",
          List(
            "import scala.scalanative.unsafe.*",
            "import scala.scalanative.unsigned.*",
            "import scala.scalanative.unsigned.UInt.*",
            "import gdext.generated.types.*"
          ),
          typeDefs ++ List(interfaceClass, companionObj)
        )
    end interfaceSource

    // ── Builtins file ─────────────────────────────────────────────────────────

    def builtinsSource(
        valueTypes: Vector[Ast.BuiltinClass],
        heapTypes: Vector[Ast.BuiltinClass]
    ): String =
        def cstructTypeName(members: Vector[Ast.BuiltinMember]): Type =
            val args = members.map(m => Type.Name(metaToScalaType(m.meta))).toList
            Type.Apply(Type.Name(s"CStruct${members.length}"), args)

        def buildTagGiven(name: String, members: Vector[Ast.BuiltinMember]): Defn.GivenAlias =
            val tpe         = Type.Apply(Type.Name("Tag"), List(Type.Name(name)))
            val materialize = Term.ApplyType(
              Term.Select(Term.Name("Tag"), Term.Name(s"materializeCStruct${members.length}Tag")),
              members.map(m => Type.Name(metaToScalaType(m.meta))).toList
            )
            buildGivenAlias(tpe, asInstanceOfTerm(materialize, tpe))
        end buildTagGiven

        def buildApplyCtor(name: String, members: Vector[Ast.BuiltinMember]): Option[Defn.Def] =
            if !members.forall(m => isPrimitiveMeta(m.meta)) then None
            else
                val ptrType = Type.Apply(Type.Name("Ptr"), List(Type.Name(name)))
                val params  = members.map { m =>
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
                  (simpleValDef("p", stackallocTerm(Type.Name(name))) :: sets) :+ Term.Name("p")
                )
                Some(buildInlineDef("apply", List(params), ptrType, body))

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
                    List(buildInlineDef(
                      fn,
                      Nil,
                      Type.Apply(Type.Name("Ptr"), List(Type.Name(tp))),
                      Term.Select(Term.Name("v"), Term.Name(s"at$idx"))
                    ))
                end if
            }.toList

        def buildMathExtMethods(name: String, members: Vector[Ast.BuiltinMember]): List[Defn.Def] =
            val metas = members.map(_.meta).distinct
            if !members.forall(m => isPrimitiveMeta(m.meta)) || metas.length != 1 then
                return List.empty
            val tp         = metaToScalaType(metas.head)
            val fields     = members.map(m => m.name).toList
            val ptrType    = Type.Apply(Type.Name("Ptr"), List(Type.Name(name)))
            val isIntegral = tp == "Int" || tp == "Long"

            val unaryFnType  = Type.Function(List(Type.Name(tp)), Type.Name(tp))
            val binaryFnType = Type.Function(List(Type.Name(tp), Type.Name(tp)), Type.Name(tp))
            val ptrParamType = Type.Apply(Type.Name("Ptr"), List(Type.Name(name)))

            def mapBody: Term =
                val sets = fields.map { f =>
                    Term.Assign(
                      Term.Select(Term.Name("result"), Term.Name(f)),
                      Term.Apply(Term.Name("f"), List(Term.Select(Term.Name("v"), Term.Name(f))))
                    )
                }
                Term.Block(
                  (simpleValDef("result", stackallocTerm(Type.Name(name))) :: sets) :+
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
                  (simpleValDef("result", stackallocTerm(Type.Name(name))) :: sets) :+
                      Term.Name("result")
                )
            end combineBody

            val mapDef = buildInlineDef(
              "map",
              List(List(Term.Param(Nil, Term.Name("f"), Some(unaryFnType), None))),
              ptrType,
              mapBody
            )
            val combineDef = buildInlineDef(
              "combine",
              List(
                List(Term.Param(Nil, Term.Name("o"), Some(ptrParamType), None)),
                List(Term.Param(Nil, Term.Name("f"), Some(binaryFnType), None))
              ),
              ptrType,
              combineBody
            )

            def scalarOp(op: String): Defn.Def = buildInlineDef(
              op,
              List(List(Term.Param(Nil, Term.Name("scalar"), Some(Type.Name(tp)), None))),
              ptrType,
              s"v.map(_ $op scalar)".parse[Term].get
            )
            def vectorOp(op: String): Defn.Def = buildInlineDef(
              op,
              List(List(Term.Param(Nil, Term.Name("o"), Some(ptrParamType), None))),
              ptrType,
              s"v.combine(o)(_ $op _)".parse[Term].get
            )

            val alwaysOps = List(scalarOp("*"), vectorOp("*"), scalarOp("/"), vectorOp("/"))
            val addSubOps =
                if isIntegral then List(vectorOp("+"), vectorOp("-"))
                else List(scalarOp("+"), scalarOp("-"), vectorOp("+"), vectorOp("-"))

            List(mapDef, combineDef) ++ alwaysOps ++ addSubOps
        end buildMathExtMethods

        val valueStats: List[Stat] = valueTypes.flatMap { b =>
            val cstruct    = cstructTypeName(b.members)
            val ptrType    = Type.Apply(Type.Name("Ptr"), List(Type.Name(b.name)))
            val extParam   = Term.Param(Nil, Term.Name("v"), Some(ptrType), None)
            val opaqueType = Defn
                .Type(List(Mod.Opaque()), Type.Name(b.name), Nil, cstruct, Type.Bounds(None, None))
            val docComment = s"/** Godot value type. Use stackalloc[${b.name}]() or ${b
                    .name}(...) to create instances. */"
            val commentedOpaque = s"$docComment\n${opaqueType.syntax}".parse[Stat].get

            val objectStats: List[Stat] = List(buildTagGiven(b.name, b.members)) ++
                buildApplyCtor(b.name, b.members).toList ++ List(buildExtensionGroup(
                  extParam,
                  Nil,
                  buildFieldExtMethods(b.name, b.members) ++ buildMathExtMethods(b.name, b.members)
                ))
            List(commentedOpaque, simpleObject(b.name, objectStats))
        }.toList

        val heapStats: List[Stat] = heapTypes
            .map(b => s"class ${b.name}(val ptr: Ptr[Byte])".parse[Stat].get).toList

        "// Generated by gdext generator — do not edit.\n" + buildSource(
          "gdext.generated",
          List("import scala.scalanative.unsafe.*", "import scala.scalanative.unsigned.*"),
          valueStats ++ heapStats
        )
    end builtinsSource

    // ── Wrapper class file ────────────────────────────────────────────────────

    def wrapperSource(
        cls: Ast.GodotClass,
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty
    ): String =
        val instanceMethods = cls.methods.filter { m =>
            !m.isVirtual && !m.isStatic && !jvmMethodConflicts.contains(toCamel(m.name))
        }
        val staticMethods = cls.methods.filter { m =>
            !m.isVirtual && m.isStatic && !jvmMethodConflicts.contains(toCamel(m.name))
        }
        val virtuals = cls.methods.filter(_.isVirtual)

        val methodDefs = instanceMethods.map(
          buildMethod(cls, _, valueBuiltins = valueBuiltins, refcountedTypes = refcountedTypes)
        )
        val staticDefs = staticMethods.map(buildMethod(
          cls,
          _,
          isStatic = true,
          valueBuiltins = valueBuiltins,
          refcountedTypes = refcountedTypes
        ))
        val virtualDefs = virtuals.flatMap(buildVirtualStub(_, valueBuiltins))

        val propStats: List[Stat] = cls.properties.flatMap { p =>
            cls.methods.find { m =>
                m.name == p.getter && !m.isVirtual && !m.isStatic && m.args.forall(_.hasDefault)
            }.map { gm =>
                val field     = toCamel(p.name)
                val retType   = godotType(gm.returnTypeName, gm.returnMeta, valueBuiltins)
                val getterDef = Defn.Def(
                  Nil,
                  Term.Name(field),
                  Nil,
                  Nil,
                  Some(retType),
                  Term.Apply(Term.Name(toCamel(p.getter)), Nil)
                )
                val setterDef = p.setter.flatMap { sName =>
                    cls.methods.find { m =>
                        m.name == sName && !m.isVirtual && !m.isStatic &&
                        m.args.count(!_.hasDefault) == 1
                    }.map { sm =>
                        val spt =
                            paramGodotType(sm.args.head.typeName, sm.args.head.meta, valueBuiltins)
                        Defn.Def(
                          Nil,
                          Term.Name(field),
                          Nil,
                          List(List(Term.Param(Nil, Term.Name("v"), Some(spt), None))),
                          Some(Type.Name("Unit")),
                          Term.Apply(Term.Name(toCamel(sName)), List(Term.Name("v")))
                        )
                    }
                }
                List(getterDef) ++ setterDef.toList
            }.getOrElse(Nil)
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
                simpleValDef(
                  "obj",
                  Term.New(Init(Type.Name(cls.name), Name.Anonymous(), List(Nil)))
                ),
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
                  Term.New(Init(
                    Type.Name(cls.name),
                    Name.Anonymous(),
                    List(List(Term.Apply(
                      Term.Select(Term.Name("GdxApi"), Term.Name("getSingleton")),
                      List(cStr(cls.name))
                    )))
                  ))
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
        val parentInits =
            List(Init(Type.Name(parentName), Name.Anonymous(), List(List(Term.Name("_p")))))
        val ctor = Ctor.Primary(
          Nil,
          Name.Anonymous(),
          List(List(Term.Param(Nil, Term.Name("_p"), Some(ptrByte), Some(Lit.Null()))))
        )
        val clsDef = Defn.Class(
          Nil,
          Type.Name(cls.name),
          Nil,
          ctor,
          Template(Nil, parentInits, Self(Name.Anonymous(), None), classStats)
        )

        val companionStats: List[Stat] = singletonStats ++ bindsObjOpt.toList ++
            ctorDefOpt.toList ++ staticDefs.toList
        val companionOpt: Option[Defn.Object] = Option
            .when(companionStats.nonEmpty)(simpleObject(cls.name, companionStats))

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
          "import gdext.core.{GdxApi, GodotObject}"
        )
        val allImports =
            if cls.name == "Node" then baseImports :+ "import scala.scalanative.libc.string.memset"
            else baseImports
        "// Generated by gdext generator — do not edit.\n" +
            buildSource("gdext.generated", allImports, topStats)
    end wrapperSource

    // ── Virtuals file ─────────────────────────────────────────────────────────

    def virtualEntryTerms(
        virtuals: Vector[(Ast.GodotMethod, String)],
        valueBuiltins: Set[String]
    ): Vector[Term] = virtuals.flatMap { (m, defClass) =>
        val camelName         = toCamel(m.name)
        val retType           = godotTypeStr(m.returnTypeName, m.returnMeta, valueBuiltins)
        val conflictsWithBase = godotClassVirtuals.get(camelName).exists(_._1 != retType)
        if conflictsWithBase then None
        else
            val dispatch = buildDispatchLambda(m, defClass, valueBuiltins)
            val entryStr = s"""VirtualEntry("${m.name}", required = ${m
                    .isRequired}, dispatch = ${dispatch.syntax})"""
            Some(entryStr.parse[Term].get)
        end if
    }

    def virtualsSource(cls: Ast.GodotClass, entries: Vector[Term]): String =
        val entriesValDef = Defn.Val(
          Nil,
          List(Pat.Var(Term.Name("entries"))),
          Some(Type.Apply(Type.Name("Vector"), List(Type.Name("VirtualEntry")))),
          Term.Apply(Term.Name("Vector"), entries.toList)
        )
        val objDef = simpleObject(s"${cls.name}Virtuals", List(entriesValDef))
        "// Generated by gdext generator — do not edit.\n" + buildSource(
          "gdext.generated",
          List(
            "import gdext.core.virtual.VirtualEntry",
            "import gdext.core.GodotObject",
            "import scala.scalanative.unsafe.*"
          ),
          List(objDef)
        )
    end virtualsSource

    // ── Utility functions file ────────────────────────────────────────────────

    def utilitySource(
        utilities: Vector[Parser.UtilityFunction],
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty
    ): String =
        val methodDefs: List[Defn.Def] = utilities.map { fn =>
            val name    = toCamel(fn.name)
            val reqArgs = if fn.isVararg then Vector.empty else fn.arguments.filterNot(_.hasDefault)
            val params: List[Term.Param] =
                if fn.isVararg then List(Term.Param(Nil, Term.Name("args"), Some(ptrPtrByte), None))
                else reqArgs.map(godotParam(_, valueBuiltins)).toList
            val (rAlloc, rRead) = retSetup(fn.returnTypeName, None, valueBuiltins, refcountedTypes)
            val retPtr: Term    =
                if rAlloc.isDefined then asInstanceOfTerm(Term.Name("_ret"), ptrByte)
                else Lit.Null()
            val callStat: Stat = Term.Apply(
              Term.Select(Term.Name("GdxApi"), Term.Name("callUtilityFunction")),
              List(
                Term.Select(Term.Name("Binds"), Term.Name(name)),
                Term.Name("_args"),
                Lit.Int(if fn.isVararg then -1 else fn.arguments.size),
                retPtr
              )
            )
            val body = setupArgStats(fn.arguments, valueBuiltins, fn.isVararg) ++ rAlloc.toList ++
                List(callStat) ++ rRead.toList
            val needsZone = !fn.isVararg &&
                reqArgs.exists(a => a.typeName == "String" || a.typeName == "StringName")
            val retType =
                if fn.returnTypeName == "void" then Type.Name("Unit")
                else godotType(fn.returnTypeName, None, valueBuiltins)
            Defn.Def(
              Nil,
              Term.Name(name),
              Nil,
              List(params),
              Some(retType),
              if needsZone then zoneWrap(body) else Term.Block(body)
            )
        }.toList

        val bindsStats: List[Stat] = utilities.map { fn =>
            lazyValDef(
              toCamel(fn.name),
              ptrByte,
              Term.Apply(
                Term.Select(Term.Name("GdxApi"), Term.Name("getUtilityFunctionPtr")),
                List(cStr(fn.name), Lit.Long(fn.hash))
              )
            )
        }.toList

        val utilitiesObj =
            simpleObject("UtilityFunctions", simpleObject("Binds", bindsStats) :: methodDefs)

        "// Generated by gdext generator — do not edit.\n" + buildSource(
          "gdext.generated",
          List(
            "import scala.scalanative.unsafe.*",
            "import scala.scalanative.unsigned.*",
            "import gdext.core.GdxApi"
          ),
          List(utilitiesObj)
        )
    end utilitySource
end TreesGenerator
