package com.julianavar.gdext
package godotscalanativelib.ffi

import godotscalanativelib.utils.ScalaFile

object Generator:
    def gdxFfi(interfaceNames: Vector[String], folder: String, file: String): Vector[ScalaFile] =
        generators.GdxFfiGenerator.generate(interfaceNames, folder, file)

    def types(json: ujson.Value, folder: String, file: String): Vector[ScalaFile] = generators
        .TypesGenerator.generate(json, folder, file)

    def interfaces(json: ujson.Value, folder: String, file: String): Vector[ScalaFile] = generators
        .InterfaceGenerator.generate(json, folder, file)

    def cstructExt(folder: String): Vector[ScalaFile] = generators.CStructExtGenerator
        .generate(folder)
end Generator
