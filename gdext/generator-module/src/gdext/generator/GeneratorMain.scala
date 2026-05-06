package gdext.generator

object GeneratorMain:
    var debugMode = false

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

        println(s"  Found ${classes.size} classes, ${builtins.size} builtin types")

        val scalaFiles = Generator.types(types.toVector) ++ Generator.interfaces(interfaces) ++
            Generator.generateBuiltins(builtins) ++ Generator.classVirtuals(classes) ++
            Generator.generateWrappers(classes)

        os.makeDir.all(outDir)

        for file <- scalaFiles do
            val filePath = file.path.split("/").foldLeft(outDir)(_ / _) / s"${file.name}.scala"
            os.makeDir.all(filePath / os.up)
            os.write.over(filePath, file.content)
            if debugMode then println(s"  wrote ${file.path}/${file.name}.scala")
        end for

        println(s"Done. Generated ${scalaFiles.size} files into $outDir")
    end main
end GeneratorMain
