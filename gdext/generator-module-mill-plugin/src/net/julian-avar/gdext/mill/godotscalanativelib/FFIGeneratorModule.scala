package net.`julian-avar`.gdext.mill
package godotscalanativelib

import mill.*
import mill.api.BuildCtx
import mainargs.arg

import godotscalanativelib.ffi.*

trait FFIGeneratorModule extends GeneratorModule:
    override def generatedSources = Task {
        BuildCtx.withFilesystemCheckerDisabled {
            val resourcesDir                        = generatorResources() / godotVersion()
            val `gdextension_interface.schema.json` = resourcesDir /
                "gdextension_interface.schema.json"
            val `gdextension_interface.json` = resourcesDir / "gdextension_interface.json"
            val `gdextension_interface.h`    = resourcesDir / "gdextension_interface.h"
            val `extension_api.json`         = resourcesDir / "extension_api.json"

            assertGeneratorResource(`gdextension_interface.json`)
            assertGeneratorResource(`extension_api.json`)
            warnIfMissingOptionalGeneratorResource(`gdextension_interface.schema.json`)
            warnIfMissingOptionalGeneratorResource(`gdextension_interface.h`)

            println(s"Reading ${`gdextension_interface.json`}...")

            val json       = ujson.read(os.read(`gdextension_interface.json`))
            val interfaces = json("interface")
            val types      = json("types")

            println(s"  Found ${interfaces.arr.size} interface functions, ${types.arr.size} types")

            val files = GdxFfiGenerator.generate(
              interfaces.arr.map(_("name").str).toVector,
              "gdext/ffi/generator/generated",
              "GdxFfi"
            ) ++ TypesGenerator.generate(types, "gdext/ffi/generator/generated", "GdExtTypes") ++
                InterfaceGenerator
                    .generate(interfaces, "gdext/ffi/generator/generated", "GdExtInterface") ++
                CStructExtGenerator.generate("gdext/ffi/generator/generated")

            writeFiles(files, Task.dest)

            println(s"Done. Generated ${files.size} files")

            Seq(PathRef(Task.dest))
        }
    }
end FFIGeneratorModule
