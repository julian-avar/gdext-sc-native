package gdext.generator
import gdext.generator.parser.Ast

import parser.Parser
import scala.meta.{Dialect, dialects}

case class ScalaFile(content: String, path: String, name: String)

trait Generator(using dialect: Dialect):
    def types(types: Vector[Ast.Type]): Vector[ScalaFile] = //
        trees.TypesGenerator().generate(types)

    def interfaces(interfaces: Vector[Ast.Interface]): Vector[ScalaFile] = //
        trees.InterfacesGenerator().generate(interfaces)

    def builtins(builtins: Vector[Ast.BuiltinClass]): Vector[ScalaFile] = //
        trees.BuiltinsGenerator().generate(builtins)

    def virtuals(
        classes: Vector[Ast.GodotClass],
        valueBuiltins: Set[String] = Set.empty
    ): Vector[ScalaFile] = //
        trees.VirtualsGenerator().generate(classes, valueBuiltins)

    def wrappers(
        classes: Vector[Ast.GodotClass],
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty
    ): Vector[ScalaFile] = //
        trees.WrappersGenerator().generate(classes, valueBuiltins, refcountedTypes)

    def utilities(
        utilities: Vector[Ast.UtilityFunction],
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty
    ): Vector[ScalaFile] = //
        trees.UtilitiesGenerator().generate(utilities, valueBuiltins, refcountedTypes)

    def globalScope(
        utilities: Vector[Ast.UtilityFunction],
        enums: Vector[Ast.GlobalEnum]
    ): Vector[ScalaFile] = trees.GlobalScopeGenerator().generate(utilities, enums)

end Generator

object Generator extends Generator(using dialects.Scala3)
