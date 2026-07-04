package com.julianavar.gdext
package godotscalanativelib.ffi

import mainargs.*

import godotscalanativelib.utils.ScalaFile

given TokensReader.Simple[os.Path]:
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))

object GeneratorMain:
    def main(args: Array[String]): Unit = Parser(this).runOrExit(args)

    /** @param interface
      * @param outFfi
      *   gdext/ffi/src/
      */
    @main()
    def main(
        @arg(positional = true)
        interface: os.Path,
        @arg(positional = true)
        out: os.Path
    ): Unit =
        println(s"Reading $interface...")

        val json       = ujson.read(os.read(interface))
        val interfaces = json("interface")
        val types      = json("types")

        println(s"  Found ${interfaces.arr.size} interface functions, ${types.arr.size} types")

        val files = Generator.gdxFfi(
          interfaces.arr.map(_("name").str).toVector,
          "gdext/ffi/generator/generated",
          "GdxFfi"
        ) ++ Generator.types(types, "gdext/ffi/generator/generated", "GdExtTypes") ++
            Generator.interfaces(interfaces, "gdext/ffi/generator/generated", "GdExtInterface") ++
            Generator.cstructExt("gdext/ffi/generator/generated")

        writeFiles(files, out)

        println(s"Done. Generated ${files.size} files")
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
