package net.`julian-avar`.gdext
package godotscalanativelib.core

case class ScalaFile(path: String, name: String, content: String)

object Generator:
    def heapBuiltins(folder: String): Vector[ScalaFile] = generators.HeapBuiltinGenerator
        .generate(folder)

    def packedArrays(folder: String, file: String): Vector[ScalaFile] = generators
        .PackedArraysGenerator.generate(folder, file)

    def stringNames(folder: String, file: String): Vector[ScalaFile] = generators
        .StringNamesGenerator.generate(folder, file)
end Generator
