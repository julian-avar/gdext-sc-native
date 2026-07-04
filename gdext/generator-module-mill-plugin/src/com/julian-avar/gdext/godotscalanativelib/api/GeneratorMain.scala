package com.`julian-avar`.gdext
package godotscalanativelib.api

import mainargs.*

import godotscalanativelib.resource_parser
import godotscalanativelib.utils.ScalaFile

given TokensReader.Simple[os.Path]:
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))

object GeneratorMain:
    def main(args: Array[String]): Unit = Parser(this).runOrExit(args)

    @main()
    def main(
        @arg(positional = true)
        interface: os.Path,
        @arg(positional = true)
        `extension`: os.Path,
        @arg(positional = true)
        out: os.Path
    ) =
        println(s"Reading $interface...")
        val interfaceJson = ujson.read(os.read(interface))
        val types         = resource_parser.Parser.types(interfaceJson("types"))
        val interfaces    = resource_parser.Parser.interfaces(interfaceJson("interface"))

        println(s"Reading $"`extension`...")
        val classJson      = ujson.read(os.read(`extension`))
        val singletonNames = resource_parser.Parser.singletonNames(classJson)
        val classes        = resource_parser.Parser.godotClasses(classJson, singletonNames)
        val builtins       = resource_parser.Parser.builtinClasses(classJson)
        val utilities      = resource_parser.Parser.utilityFunctions(classJson)
        val globalEnums    = resource_parser.Parser.globalEnums(classJson)

        println(s"  Found ${classes.size} classes, ${builtins.size} builtin types, " + s"${utilities
                .size} utility functions, ${globalEnums.size} global enums")

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

        writeFiles(files, out)

        println(s"Done. Generated ${files.size} files into $out")
    end main

    def writeFiles(files: Vector[ScalaFile], root: os.Path): Unit =
        os.makeDir.all(root)
        for file <- files do
            val filePath = file.path.split("/").foldLeft(root)(_ / _) / s"${file.name}.scala"
            os.makeDir.all(filePath / os.up)
            os.write.over(filePath, file.content)
            println(s"  Wrote $filePath")
        end for
    end writeFiles
end GeneratorMain
