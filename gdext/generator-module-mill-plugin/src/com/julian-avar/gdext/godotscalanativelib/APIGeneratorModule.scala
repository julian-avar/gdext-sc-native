package com.`julian-avar`.gdext
package godotscalanativelib

import mill.*
import mill.api.BuildCtx

import godotscalanativelib.api.Generator
import godotscalanativelib.resource_parser

trait APIGeneratorModule extends GeneratorModule:
    override def generatedSources = Task {
        BuildCtx.withFilesystemCheckerDisabled {
            val resourcesDir                        = generatorResources() / godotVersion()
            val `gdextension_interface.schema.json` = resourcesDir /
                "gdextension_interface.schema.json"
            val `gdextension_interface.json` = resourcesDir / "gdextension_interface.json"
            val `gdextension_interface.h`    = resourcesDir / "gdextension_interface.h"
            val `extension_api.json`         = resourcesDir / "extension_api.json"

            /** assert required generator resource exists */
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
            val interfaceJson = ujson.read(os.read(`gdextension_interface.json`))
            val types         = resource_parser.Parser.types(interfaceJson("types"))
            val interfaces    = resource_parser.Parser.interfaces(interfaceJson("interface"))

            println(s"Reading ${`extension_api.json`}...")
            val classJson      = ujson.read(os.read(`extension_api.json`))
            val singletonNames = resource_parser.Parser.singletonNames(classJson)
            val classes        = resource_parser.Parser.godotClasses(classJson, singletonNames)
            val builtins       = resource_parser.Parser.builtinClasses(classJson)
            val utilities      = resource_parser.Parser.utilityFunctions(classJson)
            val globalEnums    = resource_parser.Parser.globalEnums(classJson)

            println(s"  Found ${classes.size} classes, ${builtins
                    .size} builtin types, " + s"${utilities.size} utility functions, ${globalEnums
                    .size} global enums")

            val valueBuiltins: Set[String]   = builtins.filter(_.members.nonEmpty).map(_.name).toSet
            val refcountedTypes: Set[String] = classes.filter(_.isRefcounted).map(_.name).toSet

            val files = Generator.types(types.toVector, "gdext/generated/types") ++
                Generator.interfaces(interfaces, "gdext/generated", "Interface") ++
                Generator.builtins(builtins, "gdext/generated", "GodotBuiltins") ++
                Generator.virtuals(classes, valueBuiltins, "gdext/generated/virtuals") ++
                Generator
                    .wrappers(classes, valueBuiltins, refcountedTypes, "gdext/generated/classes") ++
                Generator.utilities(
                  utilities,
                  valueBuiltins,
                  refcountedTypes,
                  "gdext/generated",
                  "UtilityFunctions"
                ) ++ Generator.globalScope(utilities, globalEnums, "gdext/generated", "GlobalScope")

            writeFiles(files, Task.dest)

            println(s"Done. Generated ${files.size} files")

            Seq(PathRef(Task.dest))
        }
    }
end APIGeneratorModule
