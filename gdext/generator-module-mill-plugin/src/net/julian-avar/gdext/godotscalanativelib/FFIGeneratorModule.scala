package net.`julian-avar`.gdext
package godotscalanativelib

import mill.*
import mill.api.BuildCtx
import mainargs.arg

import godotscalanativelib.ffi.Generator

trait FFIGeneratorModule extends GeneratorModule:
    override def generatedSources = Task {
        BuildCtx.withFilesystemCheckerDisabled {
            val resourcesDir                        = generatorResources() / godotVersion()
            val `gdextension_interface.schema.json` = resourcesDir /
                "gdextension_interface.schema.json"
            val `gdextension_interface.json` = resourcesDir / "gdextension_interface.json"
            val `gdextension_interface.h`    = resourcesDir / "gdextension_interface.h"
            val `extension_api.json`         = resourcesDir / "extension_api.json"

            /** assert required generator resource exists
              *
              * @param file
              */
            def assertResource(file: os.Path) = assert(
              os.exists(file),
              s"File $file not found. Run `./mill downloadGeneratorResources` first."
            )

            /** older Godot versions ship `.h`/`.schema.json` resources; newer ones may not have
              * been backfilled yet, and neither is read by the generator, so their absence is only
              * a warning
              */
            def warnIfMissingOptionalResource(file: os.Path) =
                if !os.exists(file) then println(s"  warning: optional resource $file not found")

            assertResource(`gdextension_interface.json`)
            assertResource(`extension_api.json`)
            warnIfMissingOptionalResource(`gdextension_interface.schema.json`)
            warnIfMissingOptionalResource(`gdextension_interface.h`)

            println(s"Reading ${`gdextension_interface.json`}...")

            val json       = ujson.read(os.read(`gdextension_interface.json`))
            val interfaces = json("interface")
            val types      = json("types")

            println(s"  Found ${interfaces.arr.size} interface functions, ${types.arr.size} types")

            val files = Generator.gdxFfi(
              interfaces.arr.map(_("name").str).toVector,
              "gdext/ffi/generator/generated",
              "GdxFfi"
            ) ++ Generator.types(types, "gdext/ffi/generator/generated", "GdExtTypes") ++
                Generator
                    .interfaces(interfaces, "gdext/ffi/generator/generated", "GdExtInterface") ++
                Generator.cstructExt("gdext/ffi/generator/generated")

            writeFiles(files, Task.dest)

            println(s"Done. Generated ${files.size} files")

            Seq(PathRef(Task.dest))
        }
    }
end FFIGeneratorModule
