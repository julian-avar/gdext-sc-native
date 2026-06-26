package gdext.generator

object GeneratorMain:
    def main(args: Array[String]): Unit =
        if args.length < 3 then
            System.err.println(
              "Usage: GeneratorMain <gdextension_interface.json> <extension_api.json> <outputDir>"
            )
            sys.exit(1)
        end if

        val interfaceApiPath = os.Path(args(0))
        val classApiPath     = os.Path(args(1))
        val outDir           = os.Path(args(2))

        println(s"Reading $interfaceApiPath...")
        val interfaceJson = ujson.read(os.read(interfaceApiPath))
        val types         = parser.Parser.types(interfaceJson("types"))
        val interfaces    = parser.Parser.interfaces(interfaceJson("interface"))

        println(s"Reading $classApiPath...")
        val classJson      = ujson.read(os.read(classApiPath))
        val singletonNames = parser.Parser.singletonNames(classJson)
        val classes        = parser.Parser.godotClasses(classJson, singletonNames)
        val builtins       = parser.Parser.builtinClasses(classJson)
        val utilities      = parser.Parser.utilityFunctions(classJson)
        val globalEnums    = parser.Parser.globalEnums(classJson)

        println(s"  Found ${classes.size} classes, ${builtins.size} builtin types, " + s"${utilities
                .size} utility functions, ${globalEnums.size} global enums")

        val valueBuiltins: Set[String]   = builtins.filter(_.members.nonEmpty).map(_.name).toSet
        val refcountedTypes: Set[String] = classes.filter(_.isRefcounted).map(_.name).toSet

        val scalaFiles = Generator.types(types.toVector) ++ Generator.interfaces(interfaces) ++
            Generator.builtins(builtins) ++ Generator.virtuals(classes, valueBuiltins) ++
            Generator.wrappers(classes, valueBuiltins, refcountedTypes) ++
            Generator.utilities(utilities, valueBuiltins, refcountedTypes) ++
            Generator.globalScope(utilities, globalEnums)

        os.makeDir.all(outDir)

        for file <- scalaFiles do
            val filePath = file.path.split("/").foldLeft(outDir)(_ / _) / s"${file.name}.scala"
            os.makeDir.all(filePath / os.up)
            os.write.over(filePath, file.content)
        end for

        println(s"Done. Generated ${scalaFiles.size} files into $outDir")
    end main
end GeneratorMain
