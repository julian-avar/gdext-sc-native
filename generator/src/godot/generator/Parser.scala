package godot.generator

import upickle.default.*

/** Parser for Godot's extension_api.json file. This file describes the entire Godot API including
  * classes, methods, properties, etc.
  */
object Parser:
    /** Root structure of extension_api.json
      */
    case class ExtensionAPI(
        header: Header,
        builtin_class_sizes: List[BuiltinClassSize],
        builtin_class_member_offsets: List[BuiltinClassMemberOffset],
        global_constants: List[GlobalConstant],
        global_enums: List[GlobalEnum],
        utility_functions: List[UtilityFunction],
        builtin_classes: List[BuiltinClass],
        classes: List[Class],
        singletons: List[Singleton],
        native_structures: List[NativeStructure]
    ) derives ReadWriter

    case class Header(
        version_major: Int,
        version_minor: Int,
        version_patch: Int,
        version_status: String,
        version_build: String,
        version_full_name: String
    ) derives ReadWriter

    case class BuiltinClassSize(build_configuration: String, sizes: List[TypeSize])
        derives ReadWriter

    case class TypeSize(name: String, size: Int) derives ReadWriter

    case class BuiltinClassMemberOffset(
        build_configuration: String,
        classes: List[ClassMemberOffsets]
    ) derives ReadWriter

    case class ClassMemberOffsets(name: String, members: List[MemberOffset]) derives ReadWriter

    case class MemberOffset(member: String, offset: Int, meta: String) derives ReadWriter

    case class GlobalConstant(name: String, value: Int) derives ReadWriter

    case class GlobalEnum(name: String, is_bitfield: Boolean, values: List[EnumValue])
        derives ReadWriter

    case class EnumValue(name: String, value: Int) derives ReadWriter

    case class UtilityFunction(
        name: String,
        return_type: Option[String],
        category: String,
        is_vararg: Boolean,
        hash: Long,
        arguments: Option[List[Argument]]
    ) derives ReadWriter

    case class Argument(
        name: String,
        `type`: String,
        meta: Option[String],
        default_value: Option[String]
    ) derives ReadWriter

    case class BuiltinClass(
        name: String,
        indexing_return_type: Option[String],
        is_keyed: Boolean,
        operators: List[Operator],
        methods: Option[List[Method]],
        members: Option[List[Member]],
        constants: Option[List[Constant]],
        enums: Option[List[Enum]],
        constructors: List[Constructor],
        has_destructor: Boolean
    ) derives ReadWriter

    case class Operator(name: String, right_type: Option[String], return_type: String)
        derives ReadWriter

    case class Method(
        name: String,
        return_type: Option[String],
        is_vararg: Boolean,
        is_const: Boolean,
        is_static: Boolean,
        is_virtual: Boolean,
        hash: Option[Long],
        arguments: Option[List[Argument]]
    ) derives ReadWriter

    case class Member(name: String, `type`: String) derives ReadWriter

    case class Constant(name: String, value: Int) derives ReadWriter

    case class Enum(name: String, values: List[EnumValue]) derives ReadWriter

    case class Constructor(index: Int, arguments: Option[List[Argument]]) derives ReadWriter

    case class Class(
        name: String,
        is_refcounted: Boolean,
        is_instantiable: Boolean,
        inherits: Option[String],
        api_type: String,
        constants: Option[List[Constant]],
        enums: Option[List[Enum]],
        methods: Option[List[Method]],
        properties: Option[List[Property]],
        signals: Option[List[Signal]]
    ) derives ReadWriter

    case class Property(
        name: String,
        `type`: String,
        getter: Option[String],
        setter: Option[String],
        index: Option[Int]
    ) derives ReadWriter

    case class Signal(name: String, arguments: Option[List[Argument]]) derives ReadWriter

    case class Singleton(name: String, `type`: String) derives ReadWriter

    case class NativeStructure(name: String, format: String) derives ReadWriter

    /** Parse extension_api.json from a file path
      */
    def parseFile(path: String): ExtensionAPI =
        val json = os.read(os.Path(path))
        read[ExtensionAPI](json)
end Parser
