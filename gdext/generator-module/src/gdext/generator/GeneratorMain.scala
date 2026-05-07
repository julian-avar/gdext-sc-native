package gdext.generator

object GeneratorMain:
    var verboseMode = false

    def main(args: Array[String]): Unit =
        if args.length < 2 then
            System.err.println("Usage: GeneratorMain <extensionApiPath> <outputDir>")
            sys.exit(1)

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
        val types         = Parser.types(interfaceJson("types"))
        val interfaces    = Parser.interfaces(interfaceJson("interface"))

        println(s"Reading $classApiPath...")
        val classJson = ujson.read(os.read(classApiPath))
        val classes   = Parser.godotClasses(classJson)
        val builtins  = Parser.builtinClasses(classJson)
        val utilities = Parser.utilityFunctions(classJson)

        println(s"  Found ${classes.size} classes, ${builtins.size} builtin types, ${utilities
                .size} utility functions")

        // Names of builtin types that are opaque CStruct value types (not heap classes).
        // Class wrappers use Ptr[T] for these instead of T or new T(...).
        val valueBuiltins: Set[String] = builtins.filter(_.members.nonEmpty).map(_.name).toSet

        val scalaFiles = Generator.types(types.toVector) ++ Generator.interfaces(interfaces) ++
            Generator.generateBuiltins(builtins) ++ Generator.classVirtuals(classes) ++
            Generator.generateWrappers(classes, valueBuiltins) ++
            Generator.generateUtilityFunctions(utilities, valueBuiltins)

        os.makeDir.all(outDir)

        for file <- scalaFiles do
            val filePath = file.path.split("/").foldLeft(outDir)(_ / _) / s"${file.name}.scala"
            os.makeDir.all(filePath / os.up)
            os.write.over(filePath, file.content)
            if verboseMode then println(s"  wrote ${file.path}/${file.name}.scala")
        end for

        println(s"Done. Generated ${scalaFiles.size} files into $outDir")
    end main
end GeneratorMain
