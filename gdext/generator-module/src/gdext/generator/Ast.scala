package gdext.generator

sealed trait Ast
object Ast:
    case class Deprecated(since: String, replaceWith: String)

    type VarName         = String
    type TypeName        = String
    type TypeDescription = (TypeName, Option[String]) // (type, maybe-description)

    type Arguments = Vector[(Option[VarName], TypeDescription)]

    case class Type(
        name: String,
        kind: Kind,
        description: Option[String],
        deprecated: Option[Deprecated]
    ) extends Ast

    enum Kind(val name: String) extends Ast:
        case Enum(values: Vector[(index: Int, name: String)], isBitfield: Boolean)
            extends Kind("Enum")
        case Handle(isConst: Boolean, isUninitialized: Boolean, parent: Option[TypeName])
            extends Kind("Handle")
        case Alias(`type`: TypeName)                             extends Kind("Alias")
        case Struct(members: Vector[(VarName, TypeDescription)]) extends Kind("Struct")
        case Function(arguments: Arguments, returnValue: Option[TypeDescription])
            extends Kind("Function")
    end Kind

    case class Interface(
        name: String,
        description: Option[String],
        deprecated: Option[Deprecated],
        arguments: Arguments,
        returnValue: Option[TypeDescription],
        since: String,
        see: Vector[String],
        legacyTypeName: Option[String]
    )

    // ── extension_api.json: full class model ──────────────────────────────────

    /** Simplified return type used for virtual-stub code generation. */
    enum ReturnType:
        case Void
        case Bool
        case Int          // covers int32, int64, enum::*
        case Float
        case GodotString  // "String"
        case StringName   // "StringName"
        case PackedStringArray
        case Dictionary
        case Array        // typed or untyped arrays
        case Object       // any Object / Script / ScriptLanguage subtype
        case VoidPtr      // "void*"
        case Variant
    end ReturnType

    case class GodotArg(
        name: String,
        typeName: String,
        meta: Option[String],
        hasDefault: Boolean
    )

    case class GodotMethod(
        name: String,
        hash: Long,
        returnTypeName: String,
        returnMeta: Option[String],
        args: Vector[GodotArg],
        isStatic: Boolean,
        isVirtual: Boolean,
        isRequired: Boolean   // only meaningful for virtuals
    )

    case class GodotProperty(
        name: String,
        getter: String,
        setter: Option[String]
    )

    case class GodotClass(
        name: String,
        inherits: Option[String],
        isRefcounted: Boolean,
        isInstantiable: Boolean,
        methods: Vector[GodotMethod],
        properties: Vector[GodotProperty]
    )

    case class BuiltinClass(name: String)
end Ast
