package gdext.generator

import mill.*

trait GeneratorModule extends Module:
    val extensionApiPath = Task.Source("4.5.0/extension_api.json")

    def generate() = Task {
        println(s"Generating code from ${extensionApiPath()}...")

        val jsonStr = os.read(extensionApiPath())
        val json    = ujson.read(jsonStr)

        val types      = Parser.types(json("types"))
        val interfaces = Parser.interfaces(json("interfaces"))

        val scalaFiles = this.types(types.toVector) ++ this.interfaces(interfaces)
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
