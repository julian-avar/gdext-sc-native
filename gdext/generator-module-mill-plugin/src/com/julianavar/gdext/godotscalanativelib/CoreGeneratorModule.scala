package com.julianavar.gdext
package godotscalanativelib

import mill.*
import mill.api.BuildCtx

import godotscalanativelib.core.{Generator, GeneratorMain}

trait CoreGeneratorModule extends GeneratorModule:
    override def generatedSources = Task {
        BuildCtx.withFilesystemCheckerDisabled {
            val files = Generator.heapBuiltins("gdext/core/generator/generated") ++
                Generator.packedArrays("gdext/core/generator/generated", "PackedArrays") ++
                Generator.stringNames("gdext/core/generator/generated", "StringNames")

            GeneratorMain.writeFiles(files, Task.dest)

            println(s"Done. Generated ${files.size} files")

            Seq(PathRef(Task.dest))
        }
    }
end CoreGeneratorModule
