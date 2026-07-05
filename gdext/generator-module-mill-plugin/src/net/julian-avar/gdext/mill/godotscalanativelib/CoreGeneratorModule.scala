package net.`julian-avar`.gdext.mill
package godotscalanativelib

import mill.*
import mill.api.BuildCtx

import godotscalanativelib.core.*

trait CoreGeneratorModule extends GeneratorModule:
    override def generatedSources = Task {
        BuildCtx.withFilesystemCheckerDisabled {
            val files = HeapBuiltinGenerator.generate("gdext/core/generator/generated") ++
                PackedArraysGenerator.generate("gdext/core/generator/generated", "PackedArrays") ++
                StringNamesGenerator.generate("gdext/core/generator/generated", "StringNames")

            writeFiles(files, Task.dest)

            println(s"Done. Generated ${files.size} files")

            Seq(PathRef(Task.dest))
        }
    }
end CoreGeneratorModule
