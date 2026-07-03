package com.`julian-avar`.gdext
package godotscalanativelib

import mill.*
import mill.scalalib.*
import mill.api.BuildCtx

import godotscalanativelib.utils.ScalaFile

trait GeneratorModule extends ScalaModule:
    def millVersion: Task.Simple[String] // = "1.1.2"

    // def scalaVersion: Task.Simple[String] = Task { "3.8.4" }

    // override def jvmVersion: Task.Simple[String] = Task { "21" }

    // override def platformSuffix: Task.Simple[String] = Task { "_mill1" }

    override def mvnDeps = Task {
        Seq(
          mvn"com.lihaoyi::mill-libs:${millVersion()}",
          mvn"org.scalameta::scalameta:4.17.0",
          mvn"org.scalameta::trees:4.17.0"
        )
    }

    def godotVersion: Task.Simple[String]
    def resourcesDir: Task.Simple[os.Path]
    def scalafmtConf: Task.Simple[os.Path]

    def generatorResources: Task.Simple[os.Path] = Task { resourcesDir() }

    override def generatedSources = Task {
        BuildCtx.withFilesystemCheckerDisabled {
            val resourcesDir                        = generatorResources() / godotVersion()
            val `gdextension_interface.schema.json` = resourcesDir /
                "gdextension_interface.schema.json"
            val `gdextension_interface.json` = resourcesDir / "gdextension_interface.json"
            val `gdextension_interface.h`    = resourcesDir / "gdextension_interface.h"
            val `extension_api.json`         = resourcesDir / "extension_api.json"

            /** assert required generator resource exists */
            def assertResource(file: os.Path) =
                val msg = s"File $file not found. Run `./mill downloadGeneratorResources` first."
                assert(os.exists(file), msg)

            /** older Godot versions ship `.h`/`.schema.json` resources; newer ones may not have
              * been backfilled yet, and neither is read by the generator, so their absence is only
              * a warning
              */
            def warnIfMissingOptionalResource(file: os.Path) =
                if !os.exists(file) then println(s"  warning: optional resource $file not found")

            assertResource(`gdextension_interface.json`)
            assertResource(`extension_api.json`)
            warnIfMissingOptionalResource(`gdextension_interface.schema.json`)
            warnIfMissingOptionalResource(`gdextension_interface.h`)

            println(s"Reading ${`gdextension_interface.json`}...")

            val json       = ujson.read(os.read(`gdextension_interface.json`))
            val interfaces = json("interface")
            val types      = json("types")

            println(s"  Found ${interfaces.arr.size} interface functions, ${types.arr.size} types")

            // val files = Generator.gdxFfi(
            //   interfaces.arr.map(_("name").str).toVector,
            //   "gdext/ffi/generator/generated",
            //   "GdxFfi"
            // ) ++ Generator.types(types, "gdext/ffi/generator/generated", "GdExtTypes") ++
            //     Generator.interfaces(interfaces, "gdext/ffi/generator/generated", "GdExtInterface") ++
            //     Generator.cstructExt("gdext/ffi/generator/generated")

            // writeFiles(files, Task.dest)

            // println(s"Done. Generated ${files.size} files")

            Seq(PathRef(Task.dest))
        }
    }
    // def generate() = Task.Command {
    //     val godotVersion = this.godotVersion()
    //     val apiSource    =
    //         if godotVersion == "main" //
    //         then "extension_api.json"
    //         else
    //             val parts = godotVersion.split("\\.")
    //             s"extension_api-${parts(0)}-${parts(1)}.json"

    //     val destDir       = resourcesDir / godotVersion
    //     val interfacePath = destDir / "gdextension_interface.json"
    //     val classPath     = destDir / "extension_api.json"
    //     val apiSrcPath    = resourcesDir / "api" / apiSource

    //     assert(!os.exists(interfacePath), s"Interface file $interfacePath not found.")
    //     assert(!os.exists(apiSrcPath), s"API file $apiSrcPath not found. Run downloadApi first.")

    //     if !os.exists(classPath) //
    //     then os.copy(apiSrcPath, classPath, replaceExisting = true)

    //     val cp      = runClasspath().map(_.path.toString).mkString(sys.props("path.separator"))
    //     val javaExe = sys.props("java.home") + "/bin/java"

    //     os.proc(
    //       javaExe,
    //       "-cp",
    //       cp,
    //       mainClass().get,
    //       Seq(interfacePath.toString, classPath.toString) ++ Seq(generatedSrcDir).map(_.toString)
    //     ).call(stdout = os.Inherit, stderr = os.Inherit)

    //     val cfg = scalafmtConf
    //     if os.exists(cfg) then
    //         val genFiles = Seq(generatedSrcDir)
    //             .flatMap(dir => os.walk(dir).filter(_.ext == "scala")).map(mill.PathRef(_))
    //         mill.scalalib.scalafmt.ScalafmtWorkerModule.worker()
    //             .reformat(genFiles, mill.PathRef(cfg))
    //     end if
    // }
    //

    protected def writeFiles(files: Vector[ScalaFile], root: os.Path): Unit =
        os.makeDir.all(root)
        for file <- files do
            val filePath = file.path.split("/").foldLeft(root)(_ / _) / s"${file.name}.scala"
            os.makeDir.all(filePath / os.up)
            os.write.over(filePath, file.content)
            println(s"  Wrote $filePath")
        end for
    end writeFiles

    def downloadGeneratorResources() = Task.Command {
        val apiDir = resourcesDir() / "api"
        os.makeDir.all(apiDir)
        val files = List(
          "extension_api-4-3.json",
          "extension_api-4-4.json",
          "extension_api-4-5.json",
          "extension_api-4-6.json",
          "extension_api-4-7.json",
          "extension_api.json"
        )
        for file <- files do
            val url =
                s"https://raw.githubusercontent.com/godotengine/godot-cpp/master/gdextension/$file"
            print(s"Downloading $url ... ")
            val r = os.proc("curl", "-f", "-o", apiDir / file, url).call(check = false)
            if r.exitCode == 0 then println("ok")
            else
                println(
                  s"skipped (not in godot-cpp master — use dumpApi to get it from the binary)"
                )
            end if
        end for
    }

    def dumpApi(version: String) = Task.Command {
        val apiDir  = resourcesDir() / "api"
        val parts   = version.split("\\.")
        val outFile = apiDir / s"extension_api-${parts(0)}-${parts(1)}.json"
        os.makeDir.all(apiDir)
        println(s"Dumping class API from Godot $version binary...")
        os.proc("godot", "--headless", "--dump-extension-api").call(cwd = Task.dest)
        val dumped = Task.dest / "extension_api.json"
        if os.exists(dumped) then
            os.copy(dumped, outFile, replaceExisting = true)
            println(s"Saved to $outFile")
        else sys.error(s"godot --dump-extension-api did not produce extension_api.json")
        end if
    }

    def diffInterface(versionA: String = "4.6.1", versionB: String = "4.7.0") = Task.Command {
        def read(version: String) = BuildCtx.withFilesystemCheckerDisabled {
            val path = resourcesDir() / version / "gdextension_interface.json"
            if !os.exists(path) then sys.error(s"No interface file for $version at $path")
            val json = ujson.read(os.read(path))
            (
              json("types").arr.map(_("name").str).toSet,
              json("interface").arr.map(_("name").str).toSet
            )
        }
        end read

        val (typesA, ifaceA) = read(versionA)
        val (typesB, ifaceB) = read(versionB)

        def report(label: String, added: Set[String], removed: Set[String]): Unit =
            if added.nonEmpty then added.toList.sorted.foreach(x => println(s"  + $x  [$label]"))
            if removed.nonEmpty then
                removed.toList.sorted.foreach(x => println(s"  - $x  [$label]"))
            if added.isEmpty && removed.isEmpty then println(s"  $label: identical")
        end report

        println(s"Interface diff: $versionA → $versionB")
        report("type", typesB -- typesA, typesA -- typesB)
        report("interface fn", ifaceB -- ifaceA, ifaceA -- ifaceB)
    }
end GeneratorModule
