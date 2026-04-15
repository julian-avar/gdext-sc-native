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
end Ast
