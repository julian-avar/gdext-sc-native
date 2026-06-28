package gdext.generator
package trees

import scala.meta.*
import scala.meta.dialects
import parser.Ast
import parser.Parser

given scala3: Dialect = dialects.Scala3

// Qualified helpers accessed as `util.xxx` from _BigFile and generator files
object util:
    def formatComment(description: Option[String], deprecated: Option[Ast.Deprecated]): String =
        if description.isEmpty && deprecated.isEmpty then ""
        else
            Vector(
              Vector("/**"),
              description.toVector.flatMap { _.split("\n").map { line => s" * $line" } },
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

    def isPrimitiveMeta(meta: String): Boolean = meta match
        case "float" | "int32" | "int64" | "double" => true
        case _                                      => false

    def metaToScalaType(meta: String): String = meta match
        case "float"  => "Float"
        case "int32"  => "Int"
        case "int64"  => "Long"
        case "double" => "Double"
        case other    => other

    def toCamel(name: String): String =
        val leading = name.takeWhile(_ == '_')
        val parts   = name.dropWhile(_ == '_').split("_")
        leading + parts.zipWithIndex.map { (p, i) =>
            if i == 0 || p.isEmpty then p else p.head.toUpper.toString + p.tail
        }.mkString
    end toCamel

    val jvmMethodConflicts: Set[String] =
        Set("wait", "notify", "notifyAll", "toString", "hashCode", "finalize", "getClass")

    def topoSort(builtins: Vector[Ast.BuiltinClass]): Vector[Ast.BuiltinClass] =
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
end util

def functionDefinition(comment: String, name: String, function: Ast.Kind.Function): String =
    functionDefinitionStr(comment, name, function)

// ── String utilities ──────────────────────────────────────────────────────

val godotClassVirtuals: Map[String, (String, Int)] =
    Map("_ready" -> ("Unit", 0), "_process" -> ("Unit", 1), "_physicsProcess" -> ("Unit", 1))

// Top-level forwarding for unqualified access from _BigFile and other files
def toCamel(name: String): String = util.toCamel(name)

// ── Common tree helpers ───────────────────────────────────────────────────

val ptrByte    = Type.Apply(Type.Name("Ptr"), List(Type.Name("Byte")))
val ptrLong    = Type.Apply(Type.Name("Ptr"), List(Type.Name("Long")))
val ptrDouble  = Type.Apply(Type.Name("Ptr"), List(Type.Name("Double")))
val ptrPtrByte = Type.Apply(Type.Name("Ptr"), List(ptrByte))

def cStr(s: String): Term = Term.Interpolate(Term.Name("c"), List(Lit.String(s)), Nil)

def stackallocTerm(tp: Type, size: Option[Int] = None): Term = Term
    .Apply(Term.ApplyType(Term.Name("stackalloc"), List(tp)), size.fold(Nil)(n => List(Lit.Int(n))))

def asInstanceOfTerm(t: Term, tp: Type): Term = Term
    .ApplyType(Term.Select(t, Term.Name("asInstanceOf")), List(tp))

def derefAssign(lhs: Term, rhs: Term): Stat = Term.Assign(Term.Apply(lhs, List(Lit.Long(0L))), rhs)

def simpleValDef(name: String, rhs: Term): Stat = Defn
    .Val(Nil, List(Pat.Var(Term.Name(name))), None, rhs)

def lazyValDef(name: String, tpe: Type, rhs: Term): Stat = Defn
    .Val(List(Mod.Lazy()), List(Pat.Var(Term.Name(name))), Some(tpe), rhs)

def zoneWrap(body: List[Stat]): Term = Term.Apply(Term.Name("Zone"), List(Term.Block(body)))

def emptyTemplate(stats: List[Stat]): Template =
    Template(Nil, Nil, Self(Name.Anonymous(), None), stats)

def simpleObject(name: String, stats: List[Stat]): Defn.Object = Defn
    .Object(Nil, Term.Name(name), emptyTemplate(stats))

def buildInlineDef(
    name: String,
    paramss: List[List[Term.Param]],
    retType: Type,
    body: Term
): Defn.Def = Defn.Def(List(Mod.Inline()), Term.Name(name), Nil, paramss, Some(retType), body)

def buildExtensionGroup(
    extParam: Term.Param,
    usingParams: List[Term.Param] = Nil,
    methods: List[Defn.Def]
): Defn.ExtensionGroup =
    val paramss = List(List(extParam)) ++ (if usingParams.nonEmpty then List(usingParams) else Nil)
    Defn.ExtensionGroup(Nil, paramss, Term.Block(methods))
end buildExtensionGroup

def buildGivenAlias(tpe: Type, rhs: Term): Defn.GivenAlias = Defn
    .GivenAlias(Nil, Name.Anonymous(), Nil, Nil, tpe, rhs)

def buildVarDef(name: String, tpe: Type, rhs: Term): Defn.Var = Defn
    .Var(Nil, List(Pat.Var(Term.Name(name))), Some(tpe), Some(rhs))

def buildUsingParam(name: String, tpe: Type): Term.Param = Term
    .Param(List(Mod.Using()), Term.Name(name), Some(tpe), None)

// ── Type mapping ──────────────────────────────────────────────────────────

def godotTypeStr(
    godotType: String,
    meta: Option[String],
    valueBuiltins: Set[String] = Set.empty
): String = godotType match
    case "void"       => "Unit"
    case "bool"       => "Boolean"
    case "int"        => if meta.exists(_.contains("32")) then "Int" else "Long"
    case "float"      => if meta.contains("float") then "Float" else "Double"
    case "String"     => "String"
    case "StringName" => "CString"
    case t if t.startsWith("typedarray::") => "Ptr[Byte]"
    case t if t.startsWith("enum::")       => "Int"
    case t if t.startsWith("bitfield::")   => "Int"
    case "Variant" | "void*" | "Array"     => "Ptr[Byte]"
    case t if t.endsWith("*")              => "Ptr[Byte]"
    case t if valueBuiltins.contains(t)    => t
    case t                                 => t

def paramGodotTypeStr(
    godotType: String,
    meta: Option[String],
    valueBuiltins: Set[String] = Set.empty
): String = godotType match
    case "String" | "StringName" => "String"
    case other                   => godotTypeStr(other, meta, valueBuiltins)

def godotType(t: String, meta: Option[String], vb: Set[String] = Set.empty): Type = t match
    case "void"       => Type.Name("Unit")
    case "bool"       => Type.Name("Boolean")
    case "int"        => Type.Name(if meta.exists(_.contains("32")) then "Int" else "Long")
    case "float"      => Type.Name(if meta.contains("float") then "Float" else "Double")
    case "String"     => Type.Name("String")
    case "StringName" => Type.Name("CString")
    case t if t.startsWith("typedarray::")                         => ptrByte
    case t if t.startsWith("enum::") || t.startsWith("bitfield::") => Type.Name("Int")
    case "Variant" | "void*" | "Array"                             => ptrByte
    case t if t.endsWith("*")                                      => ptrByte
    case t if vb.contains(t)                                       => Type.Name(t)
    case t                                                         => Type.Name(t)

def paramGodotType(t: String, meta: Option[String], vb: Set[String] = Set.empty): Type = t match
    case "String" | "StringName" => Type.Name("String")
    case other                   => godotType(other, meta, vb)

// ── Low-level statement builders ──────────────────────────────────────────

private def packArg(arg: Ast.GodotArg, i: Int, vb: Set[String] = Set.empty): (List[Stat], Term) =
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
                if arg.meta.exists(_.contains("32")) then Term.Select(param, Term.Name("toLong"))
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
            // Use the process-lifetime cache — no Zone or allocation at the call site.
            val cached = Term
                .Apply(Term.Select(Term.Name("StringNames"), Term.Name("cached")), List(param))
            (Nil, cached)
        case "Variant" | "void*" | "Array"     => (Nil, param)
        case t if t.startsWith("typedarray::") => (Nil, param)
        case t if t.endsWith("*")              => (Nil, param)
        case t if vb.contains(t)               => (Nil, asInstanceOfTerm(param, ptrByte))
        case _                                 => (Nil, Term.Select(param, Term.Name("ptr")))
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

// All packed array types are 16 bytes on float_64 (confirmed from extension_api.json).
// Methods returning these allocate a heap buffer via malloc so the caller can own and free it
// (typically via .toSeq / .toArray which call the destructor then free automatically).
// 16-byte opaque value types: methods returning these need a 16-byte malloc'd return buffer,
// not the default 8-byte stackalloc[Ptr[Byte]] used for engine-class pointer returns.
private val packedBuiltinSize: Map[String, Int] = Map(
    "PackedByteArray" -> 16, "PackedInt32Array" -> 16, "PackedInt64Array" -> 16,
    "PackedFloat32Array" -> 16, "PackedFloat64Array" -> 16, "PackedStringArray" -> 16,
    "PackedVector2Array" -> 16, "PackedVector3Array" -> 16, "PackedVector4Array" -> 16,
    "PackedColorArray" -> 16,
    "Callable" -> 16, "Signal" -> 16
)

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
                Term.Select(Term.ApplyUnary(Term.Name("!"), Term.Name("_ret")), Term.Name("toInt"))
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
    case "String" =>
        val alloc = simpleValDef("_ret", stackallocTerm(Type.Name("Byte"), Some(8)))
        val read  = Term.Apply(
          Term.Select(Term.Name("GdxApi"), Term.Name("godotStringToScala")),
          List(Term.Name("_ret"))
        )
        (Some(alloc), Some(read))
    case t if vb.contains(t) =>
        val tType = Type.Name(t)
        // alloc[Byte](n) is Zone-aware; the caller must supply `using Zone`.
        // This replaces the old malloc which leaked (never freed).
        val allocTerm = Term.Apply(
          Term.ApplyType(Term.Name("alloc"), List(Type.Name("Byte"))),
          List(Term.Select(Term.Name(t), Term.Name("byteSize")))
        )
        val alloc = simpleValDef("_ret", asInstanceOfTerm(allocTerm, tType))
        (Some(alloc), Some(Term.Name("_ret")))
    case t if packedBuiltinSize.contains(t) =>
        // Packed arrays are 16-byte value types. Heap-allocate so the caller owns the buffer;
        // they free it via .toSeq / .toArray which call the destructor + free automatically.
        val byteSize = packedBuiltinSize(t)
        val alloc    = simpleValDef(
          "_ret",
          Term.Apply(Term.Name("malloc"), List(Lit.Int(byteSize)))
        )
        val read = newEngineAnonymous(t, List(List(Term.Name("_ret"))))
        (Some(alloc), Some(read))
    case t =>
        val alloc      = simpleValDef("_ret", stackallocTerm(ptrByte))
        val read: Term = t match
            case "StringName" | "Variant" | "void*" | "Array" => Term
                    .ApplyUnary(Term.Name("!"), Term.Name("_ret"))
            case t2 if t2.startsWith("typedarray::") =>
                Term.ApplyUnary(Term.Name("!"), Term.Name("_ret"))
            case t2 if refcountedTypes.contains(t2) =>
                Term.Block(List(
                  simpleValDef(
                    "_r",
                    newEngineAnonymous(
                      t2,
                      List(List(Term.ApplyUnary(Term.Name("!"), Term.Name("_ret"))))
                    )
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
            case t2 => newEngineAnonymous(
                  t2,
                  List(List(Term.ApplyUnary(Term.Name("!"), Term.Name("_ret"))))
                )
        (Some(alloc), Some(read))
end retSetup

// ── Engine-class construction ─────────────────────────────────────────────

// All generated engine wrapper classes are abstract (Phase 6). Instantiation always
// goes through an anonymous concrete subclass: `new Node2D(p) {}`.
def newEngineAnonymous(typeName: String, argss: List[List[Term]]): Term = Term
    .NewAnonymous(Template(
      Nil,
      List(Init(Type.Name(typeName), Name.Anonymous(), argss)),
      Self(Name.Anonymous(), None),
      Nil
    ))

// ── Tree assembly helpers ─────────────────────────────────────────────────

private def setupArgStats(
    args: Vector[Ast.GodotArg],
    vb: Set[String],
    isVararg: Boolean = false
): List[Stat] =
    if isVararg then List(simpleValDef("_args", Term.Name("args")))
    else if args.isEmpty then List(simpleValDef("_args", asInstanceOfTerm(Lit.Null(), ptrPtrByte)))
    else
        val reqArgs   = args.filterNot(_.hasDefault)
        val optArgs   = args.filter(_.hasDefault)
        val reqPacked = reqArgs.zipWithIndex.map((a, i) => packArg(a, i, vb))
        val optPacked = optArgs.zipWithIndex.map((a, i) => packDefaultArg(a, reqArgs.size + i, vb))
        val argsAlloc = simpleValDef("_args", stackallocTerm(ptrByte, Some(args.size)))
        val assigns   = (reqPacked ++ optPacked).zipWithIndex.flatMap { case ((setup, expr), i) =>
            val assign: Stat = Term.Assign(Term.Apply(Term.Name("_args"), List(Lit.Int(i))), expr)
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
                    asInstanceOfTerm(argI, Type.Apply(Type.Name("Ptr"), List(Type.Name("Byte"))))
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
                if a.meta.contains("float") then Term.Select(deref, Term.Name("toFloat")) else deref
            case "String" | "StringName" => asInstanceOfTerm(Lit.Null(), Type.Name("String"))
            case t if t.startsWith("enum::") || t.startsWith("bitfield::") =>
                Term.Select(
                  Term.ApplyUnary(Term.Name("!"), asInstanceOfTerm(argI, ptrLong)),
                  Term.Name("toInt")
                )
            case t if valueBuiltins.contains(t)    => asInstanceOfTerm(argI, Type.Name(t))
            case "Variant" | "void*" | "Array"     => argI
            case t if t.startsWith("typedarray::") => argI
            case t if t.endsWith("*")              => argI
            case t                                 => newEngineAnonymous(
                  t,
                  List(List(Term.ApplyUnary(Term.Name("!"), asInstanceOfTerm(argI, ptrPtrByte))))
                )
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
            if t == "String" || t == "StringName" || t == "Array" ||
                t == "Variant" || t == "void*" || t.startsWith("typedarray::") || t.endsWith("*") ||
                valueBuiltins.contains(t) || packedBuiltinSize.contains(t) => List(callExpr)
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

// ── Typed ptrcall dispatch (Ptrcall.callVoidN / Ptrcall.callN) ───────────────

private def isPrimArg(a: Ast.GodotArg): Boolean = a.typeName match
    case "bool" | "int" | "float"                                  => true
    case t if t.startsWith("enum::") || t.startsWith("bitfield::") => true
    case _                                                         => false

private def isPrimRet(typeName: String): Boolean = typeName match
    case "void" | "bool" | "int" | "float"                         => true
    case t if t.startsWith("enum::") || t.startsWith("bitfield::") => true
    case _                                                         => false

private def buildTypedCall(
    cls: Ast.GodotClass,
    m: Ast.GodotMethod,
    isStatic: Boolean
): Option[Term] =
    if m.args.exists(_.hasDefault) then return None // optional args need default packing
    if m.args.size > 6 then return None
    if !isPrimRet(m.returnTypeName) then return None
    if !m.args.forall(isPrimArg) then return None

    val arity      = m.args.size
    val isVoid     = m.returnTypeName == "void"
    val methodName = if isVoid then s"callVoid$arity" else s"call$arity"
    val bindExpr   = Term
        .Select(Term.Select(Term.Name(cls.name), Term.Name("Binds")), Term.Name(toCamel(m.name)))
    val selfArg: Term = if isStatic then Lit.Null() else Term.Name("ptr")
    val argTerms      = m.args.map(a => Term.Name(toCamel(a.name))).toList
    Some(Term.Apply(
      Term.Select(Term.Name("Ptrcall"), Term.Name(methodName)),
      List(bindExpr, selfArg) ++ argTerms
    ))
end buildTypedCall

private def methodBody(
    cls: Ast.GodotClass,
    m: Ast.GodotMethod,
    isStatic: Boolean,
    valueBuiltins: Set[String],
    refcountedTypes: Set[String]
): Term = buildTypedCall(cls, m, isStatic).getOrElse {
    val name            = toCamel(m.name)
    val (rAlloc, rRead) = retSetup(m.returnTypeName, m.returnMeta, valueBuiltins, refcountedTypes)
    val retPtr: Term    =
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
    val body              = setupArgStats(m.args, valueBuiltins) ++ rAlloc.toList ++ List(callStat) ++
        rRead.toList
    val returnsVB     = valueBuiltins.contains(m.returnTypeName)
    val hasStringArgs = m.args.filterNot(_.hasDefault).exists(_.typeName == "String")
    // Value-builtin returns: Zone supplied by caller via `using Zone` param in method signature.
    // String-only args: wrap in local Zone for toCString.
    if returnsVB then Term.Block(body)
    else if hasStringArgs then zoneWrap(body)
    else Term.Block(body)
}
end methodBody

def buildMethod(
    cls: Ast.GodotClass,
    m: Ast.GodotMethod,
    isStatic: Boolean = false,
    valueBuiltins: Set[String] = Set.empty,
    refcountedTypes: Set[String] = Set.empty
): Defn.Def =
    val name       = toCamel(m.name)
    val userParams = m.args.filterNot(_.hasDefault).map(godotParam(_, valueBuiltins)).toList
    val retType    = godotType(m.returnTypeName, m.returnMeta, valueBuiltins)
    val body       = methodBody(cls, m, isStatic, valueBuiltins, refcountedTypes)
    // Value-builtin-returning methods also accept String args via toCString in the same Zone.
    // Using a caller-supplied Zone is cleaner than malloc (leak-free) and safer than stackalloc.
    // Value-builtin returns use alloc[Byte](n) which requires Zone from the caller.
    // The using clause is a separate parameter list — `using` params can't mix with regular ones.
    val returnsVB  = valueBuiltins.contains(m.returnTypeName)
    val paramLists =
      if returnsVB then
        List(userParams, List(buildUsingParam("_zone", Type.Name("Zone"))))
      else List(userParams)
    Defn.Def(Nil, Term.Name(name), Nil, paramLists, Some(retType), body)
end buildMethod

def buildVirtualStub(m: Ast.GodotMethod, valueBuiltins: Set[String] = Set.empty): Option[Defn.Def] =
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
        case t if valueBuiltins.contains(t)                            =>
            // Opaque value types (Vector2, Transform2D, AABB, etc.) don't accept null directly.
            Term.ApplyType(Term.Select(Lit.Null(), Term.Name("asInstanceOf")), List(retType))
        case _ => Lit.Null()
    Some(Defn.Def(mods, Term.Name(camelName), Nil, List(params), Some(retType), defaultBody))
end buildVirtualStub

/** Builds an allocation term + name for a value-builtin property getter.
  *
  * Uses `stackalloc` so there is NO memory leak:
  *  - For write-only access (`velocity = v`): the getter is never called at
  *    runtime — Scala only reads its type at compile time for desugaring to
  *    `velocity_=`. Zero cost, zero allocation.
  *  - For immediate inline reads (`velocity.x`, `if velocity.y > 0`): the
  *    stackalloc is valid during the expression evaluation before it returns.
  *  - For stored reads (`val v = velocity; later; v.x`): use
  *    `getVelocity()(using zone)` which provides a Zone-managed pointer that
  *    lives as long as the Zone block.
  */
def stackallocVBRetAlloc(typeName: String): (Stat, Term) =
    val allocTerm = Term.Apply(
      Term.ApplyType(Term.Name("stackalloc"), List(Type.Name("Byte"))),
      List(Term.Select(Term.Name(typeName), Term.Name("byteSize")))
    )
    val allocStat = simpleValDef("_ret", asInstanceOfTerm(allocTerm, Type.Name(typeName)))
    (allocStat, Term.Name("_ret"))
end stackallocVBRetAlloc

def buildForwardingMethod(
    cls: Ast.GodotClass,
    m: Ast.GodotMethod,
    valueBuiltins: Set[String] = Set.empty
): Defn.Def =
    val name      = toCamel(m.name)
    val reqArgs   = m.args.filterNot(_.hasDefault)
    val userParams = reqArgs.map(godotParam(_, valueBuiltins)).toList
    val retType   = godotType(m.returnTypeName, m.returnMeta, valueBuiltins)
    val callArgs  = reqArgs.map(a => Term.Name(toCamel(a.name))).toList
    val body      = Term.Apply(Term.Select(Term.Name("singleton"), Term.Name(name)), callArgs)
    val returnsVB = valueBuiltins.contains(m.returnTypeName)
    val paramLists =
      if returnsVB then List(userParams, List(buildUsingParam("_zone", Type.Name("Zone"))))
      else List(userParams)
    Defn.Def(Nil, Term.Name(name), Nil, paramLists, Some(retType), body)
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
    val argTypes = arguments.map(a => Parser.typeName(a.typeDescription.typeName).parse[Type].get)
    val retType  = returnValue.map(r => Parser.typeName(r.typeName).parse[Type].get)
        .getOrElse(Type.Name("Unit"))
    val cfuncPtrType = Type
        .Apply(Type.Name(s"CFuncPtr${arguments.length}"), (argTypes :+ retType).toList)
    Defn.Type(Nil, Type.Name(name), Nil, cfuncPtrType, Type.Bounds(None, None))
end functionDefStat

def functionDefinitionStr(comment: String, name: String, function: Ast.Kind.Function): String =
    val stat = functionDefStat(name, function)
    if comment.nonEmpty then s"$comment\n${stat.syntax}\n" else s"${stat.syntax}\n"
