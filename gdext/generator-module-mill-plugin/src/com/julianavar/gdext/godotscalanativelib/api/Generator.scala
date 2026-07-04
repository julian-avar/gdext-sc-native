package com.julianavar.gdext
package godotscalanativelib.api

import godotscalanativelib.resource_parser.Ast
import godotscalanativelib.resource_parser.Parser
import godotscalanativelib.utils.ScalaFile
import scala.meta.{Dialect, dialects}

trait Generator(using dialect: Dialect):
    def types(types: Vector[Ast.Type], folder: String): Vector[ScalaFile] = //
        generators.TypesGenerator().generate(types, folder)

    def interfaces(
        interfaces: Vector[Ast.Interface],
        folder: String,
        file: String
    ): Vector[ScalaFile] = //
        generators.InterfacesGenerator().generate(interfaces, folder, file)

    def builtins(
        builtins: Vector[Ast.BuiltinClass],
        folder: String,
        file: String
    ): Vector[ScalaFile] = //
        generators.BuiltinsGenerator().generate(builtins, folder, file)

    def virtuals(
        classes: Vector[Ast.GodotClass],
        valueBuiltins: Set[String] = Set.empty,
        folder: String
    ): Vector[ScalaFile] = //
        generators.VirtualsGenerator().generate(classes, valueBuiltins, folder)

    def wrappers(
        classes: Vector[Ast.GodotClass],
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty,
        folder: String
    ): Vector[ScalaFile] = //
        generators.WrappersGenerator().generate(classes, valueBuiltins, refcountedTypes, folder)

    def utilities(
        utilities: Vector[Ast.UtilityFunction],
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty,
        folder: String,
        file: String
    ): Vector[ScalaFile] = //
        generators.UtilitiesGenerator()
            .generate(utilities, valueBuiltins, refcountedTypes, folder, file)

    def globalScope(
        utilities: Vector[Ast.UtilityFunction],
        enums: Vector[Ast.GlobalEnum],
        folder: String,
        file: String
    ): Vector[ScalaFile] = generators.GlobalScopeGenerator()
        .generate(utilities, enums, folder, file)

end Generator

object Generator extends Generator(using dialects.Scala3)
