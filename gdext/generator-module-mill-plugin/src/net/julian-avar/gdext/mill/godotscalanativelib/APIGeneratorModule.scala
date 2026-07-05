package net.`julian-avar`.gdext.mill
package godotscalanativelib

import mill.*
import mill.api.BuildCtx

// import gdext.mill.godotscalanativelib.api.Generator
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

            assertGeneratorResource(`gdextension_interface.json`)
            assertGeneratorResource(`extension_api.json`)
            warnIfMissingOptionalGeneratorResource(`gdextension_interface.schema.json`)
            warnIfMissingOptionalGeneratorResource(`gdextension_interface.h`)

            println(s"Reading ${`gdextension_interface.json`}...")
            val interfaceJson = ujson.read(os.read(`gdextension_interface.json`))
            val types         = resource_parser.Parser.types(interfaceJson("types"))
            val interfaces    = resource_parser.Parser.interfaces(interfaceJson("interface"))

            val docClassesDir = resourcesDir / "doc_classes"
            val docClasses    = resource_parser.DocParser.parse(docClassesDir)
            if docClasses.isEmpty then
                println(
                  s"  warning: no vendored docs at $docClassesDir — descriptions will be empty (run `vendorDocClasses` first)"
                )
            else println(s"  Found docs for ${docClasses.size} classes")
            end if

            println(s"Reading ${`extension_api.json`}...")
            val classJson      = ujson.read(os.read(`extension_api.json`))
            val singletonNames = resource_parser.Parser.singletonNames(classJson)
            val classes = resource_parser.Parser.godotClasses(classJson, singletonNames, docClasses)
            val builtins    = resource_parser.Parser.builtinClasses(classJson, docClasses)
            val utilities   = resource_parser.Parser.utilityFunctions(classJson, docClasses)
            val globalEnums = resource_parser.Parser.globalEnums(classJson, docClasses)

            println(s"  Found ${classes.size} classes, ${builtins.size} builtin types, ${utilities
                    .size} utility functions, ${globalEnums.size} global enums")

            val valueBuiltins: Set[String]   = builtins.filter(_.members.nonEmpty).map(_.name).toSet
            val refcountedTypes: Set[String] = classes.filter(_.isRefcounted).map(_.name).toSet

            import godotscalanativelib.api.*
            val files = TypesGenerator().generate(types.toVector, "gdext/generated/types") ++
                InterfacesGenerator().generate(interfaces, "gdext/generated", "Interface") ++
                BuiltinsGenerator().generate(builtins, "gdext/generated", "GodotBuiltins") ++
                VirtualsGenerator().generate(classes, valueBuiltins, "gdext/generated/virtuals") ++
                WrappersGenerator()
                    .generate(classes, valueBuiltins, refcountedTypes, "gdext/generated/classes") ++
                UtilitiesGenerator().generate(
                  utilities,
                  valueBuiltins,
                  refcountedTypes,
                  "gdext/generated",
                  "UtilityFunctions"
                ) ++ GlobalScopeGenerator()
                    .generate(utilities, globalEnums, "gdext/generated", "GlobalScope")

            writeFiles(files, Task.dest)

            println(s"Done. Generated ${files.size} files")

            Seq(PathRef(Task.dest))
        }
    }
end APIGeneratorModule
