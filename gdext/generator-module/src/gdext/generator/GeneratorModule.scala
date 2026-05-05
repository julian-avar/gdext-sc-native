package gdext.generator

import mill.*

trait GeneratorModule extends Module:
    def extensionApiPath = Task.Source(moduleDir / "resources" / "4.5.0" / "gdextension_interface.json")

    def generatedDir: os.Path = moduleDir / os.up / "generated" / "src" / "gdext" / "generated"

    def generate() = Task.Command {
        println(s"Generating code from ${extensionApiPath().path}...")

        val jsonStr = os.read(extensionApiPath().path)
        val json    = ujson.read(jsonStr)

        val types      = Parser.types(json("types"))
        val interfaces = Parser.interfaces(json("interface"))

        val scalaFiles = Generator.types(types.toVector) ++ Generator.interfaces(interfaces)

        val outDir = generatedDir
        os.makeDir.all(outDir)

        for file <- scalaFiles do
            val filePath = outDir / s"${file.name}.scala"
            os.write.over(filePath, file.content)
            println(s"  wrote $filePath")

        println(s"Generated ${scalaFiles.size} files into $outDir")
    }
end GeneratorModule

// end main

//     Generator.generate(
//   extensionApiPath = os.resources / "4.5.0",
//   ???
// //   jsonPath = os.Path(os.resource / "4.5.0/gdextension_interface.json"),
// //   codegenPath = os.pwd /
// //       "modules/scala-native-gdextension/src/main/scala/io/github/optical002/godot/codegen/gdextensioninterface"
// )
