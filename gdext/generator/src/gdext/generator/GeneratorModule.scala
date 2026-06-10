package gdext.generator

import mill.*

trait GeneratorModule extends Module:
    def interfaceApiPath = Task
        .Source(moduleDir / "resources" / "4.5.0" / "gdextension_interface.json")
    def classApiPath = Task.Source(moduleDir / "resources" / "4.5.0" / "extension_api.json")

    def generatedDir: os.Path = moduleDir / os.up / "generated" / "src" / "gdext" / "generated"

    def generate() = Task.Command {
        val interfacePath = interfaceApiPath().path
        val classPath     = classApiPath().path
        println(s"Generating code from $interfacePath and $classPath...")

        val interfaceJson = ujson.read(os.read(interfacePath))
        val types         = Parser.types(interfaceJson("types"))
        val interfaces    = Parser.interfaces(interfaceJson("interface"))

        val classJson = ujson.read(os.read(classPath))
        val classes   = Parser.godotClasses(classJson)

        println(s"  Found ${classes.size} classes with virtual methods")

        val scalaFiles = Generator.types(types.toVector) ++ Generator.interfaces(interfaces) ++
            Generator.classVirtuals(classes) ++ Generator.generateWrappers(classes)

        val outDir = generatedDir
        os.makeDir.all(outDir)

        for file <- scalaFiles do
            val filePath = outDir / s"${file.name}.scala"
            os.write.over(filePath, file.content)
            println(s"  wrote $filePath")
        end for

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
