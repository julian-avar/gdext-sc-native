package com.`julian-avar`.gdext
package godotscalanativelib.core

import mainargs.*

given TokensReader.Simple[os.Path]:
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))

object GeneratorMain:
    def main(args: Array[String]): Unit = Parser(this).runOrExit(args)

    /** @param out
      *   gdext/core/src/
      */
    @main()
    def main(
        @arg(positional = true)
        out: os.Path
    ): Unit =
        val files = Vector(
          Generator.heapBuiltins("gdext/core/generator/generated"),
          Generator.packedArrays("gdext/core/generator/generated", "PackedArrays"),
          Generator.stringNames("gdext/core/generator/generated", "StringNames")
        ).flatten

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
