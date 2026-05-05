package gdext.generator

object GeneratorMain:
    def main(args: Array[String]): Unit =
        if args.length < 2 then
            System.err.println("Usage: GeneratorMain <extensionApiPath> <outputDir>")
            sys.exit(1)

        val apiPath = os.Path(args(0))
        val outDir  = os.Path(args(1))

        println(s"Reading $apiPath...")
        val json = ujson.read(os.read(apiPath))

        val types      = Parser.types(json("types"))
        val interfaces = Parser.interfaces(json("interface"))

        val scalaFiles = Generator.types(types.toVector) ++ Generator.interfaces(interfaces)

        os.makeDir.all(outDir)

        for file <- scalaFiles do
            val filePath = outDir / s"${file.name}.scala"
            os.write.over(filePath, file.content)
            println(s"  wrote ${file.name}.scala")

        println(s"Done. Generated ${scalaFiles.size} files into $outDir")
end GeneratorMain
