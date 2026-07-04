package com.julianavar.gdext
package godotscalanativelib

import mill.*
import mill.scalanativelib.*
import mill.scalanativelib.api.{ReleaseMode, BuildTarget}

trait GodotScalaNativeModule extends ScalaNativeModule:
    def godotVersion: String

    override def releaseMode       = ReleaseMode.Debug
    override def nativeBuildTarget = BuildTarget.LibraryDynamic
    override def nativeBaseName    = "godot-scala-native"

    // Sources live directly in example/, not example/src/
    override def sources = Task.Sources(moduleDir)

    // moduleDir doubles as this standalone build's mill output root, so the broad
    // `sources` scan above rediscovers the same files `generatedSources` below already
    // writes under `out/generatedSources.dest` — dedup by path so each is compiled once,
    // instead of excluding `out/` wholesale (which would drop the only copy).
    override def allSourceFiles = Task { super.allSourceFiles().distinctBy(_.path) }

    override def nativeClang   = Task.Input { PathRef(os.Path(Task.env.getOrElse("CC", "clang"))) }
    override def nativeClangPP = Task
        .Input { PathRef(os.Path(Task.env.getOrElse("CXX", "clang++"))) }

    def buildExtension() = Task.Command {
        val lib  = nativeLink()
        val dest = moduleDir / "lib"
        os.makeDir.all(dest)
        os.copy(
          lib.path,
          dest / s"lib${nativeBaseName()}.so",
          followLinks = true,
          replaceExisting = true,
          copyAttributes = false,
          createFolders = true,
          mergeFolders = false
        )
        println(s"Deployed to $dest")
    }

    /** Scans sources and generates registration + entry boilerplate.
      *
      * Emits up to two files into Task.dest:
      *
      *   - `GeneratedRegistrations.scala` — `registerAll()` calling `Register.auto[X]()` for each
      *     `@gdclass` class found. Only emitted when at least one such class exists.
      *   - `GeneratedEntry.scala` — the `@exported("godot_scala_init")` entry function. Only
      *     emitted when no user source file already defines `@exported` (i.e. the user has no
      *     custom entry). Calls `registerAll()` then `gdext.GodotEntry.init(...)`.
      *
      * A source fingerprint (content hash) is embedded in each header so Zinc recompiles when any
      * game source changes.
      */
    override def generatedSources = Task {
        val regFile     = Task.dest / "GeneratedRegistrations.scala"
        val entryFile   = Task.dest / "GeneratedEntry.scala"
        val signalsFile = Task.dest / "GeneratedSignalHandles.scala"
        val srcRoots    = sources().map(_.path).filter(os.exists)

        val gdclassPattern = raw"(?s)@gdclass\s+class\s+(\w+)".r
        val pkgPattern     = raw"package\s+([\w.]+)".r
        val signalPattern  = raw"@signal\s+case\s+class\s+(\w+)\s*\(([^)]*)\)".r

        def toSnakeCase(s: String): String = s.foldLeft("")((a, c) =>
            if c.isUpper && a.nonEmpty then a + "_" + c.toLower else a + c.toLower
        ).dropWhile(_ == '_')

        val allUserFiles = srcRoots.flatMap { root =>
            os.walk(root).filter(p => p.ext == "scala" && !p.last.startsWith("Generated"))
        }

        // Collect (package, className) for every @gdclass class.
        val found: Seq[(String, String)] = allUserFiles.flatMap { file =>
            val content = os.read(file)
            if !content.contains("@gdclass") then Seq.empty
            else
                val pkg = pkgPattern.findFirstMatchIn(content).map(_.group(1)).getOrElse("")
                gdclassPattern.findAllMatchIn(content).map(m => (pkg, m.group(1))).toSeq
            end if
        }

        // Collect @signal case class definitions, associated to their enclosing @gdclass.
        // (pkg, className, signalName, paramTypes)
        val signalAssocs: Seq[(String, String, String, List[String])] = allUserFiles
            .flatMap { file =>
                val content = os.read(file)
                if !content.contains("@signal") then Seq.empty
                else
                    val pkg     = pkgPattern.findFirstMatchIn(content).map(_.group(1)).getOrElse("")
                    val classes = gdclassPattern.findAllMatchIn(content).toVector
                    signalPattern.findAllMatchIn(content).toVector.flatMap { sm =>
                        classes.filter(_.start < sm.start).lastOption.map { cm =>
                            val paramsStr = sm.group(2).trim
                            val types     =
                                if paramsStr.isEmpty then Nil
                                else paramsStr.split(",").toList.map(_.split(":").last.trim)
                            (pkg, cm.group(1), sm.group(1), types)
                        }
                    }
                end if
            }

        // Check whether any user file already defines the entry point.
        val userHasEntry = allUserFiles.exists(f => os.read(f).contains("@exported"))

        val out = collection.mutable.Buffer.empty[PathRef]

        // Emit signal handle extension methods.
        // Uses `def` (not lazy val) to avoid extension-doesn't-support-lazy-val limitation.
        // Inside the @gdclass class, `signalName` calls the extension on `this` automatically.
        if signalAssocs.nonEmpty then
            val sigPkg  = signalAssocs.head._1
            val grouped = signalAssocs.groupBy(t => (t._1, t._2))
            val blocks  = grouped.toList.sortBy(_._1._2).map { case ((p, cls), sigs) =>
                def signalDef(sigName: String, types: List[String]): String =
                    val valName   = sigName.head.toLower.toString + sigName.tail
                    val godotName = toSnakeCase(sigName)
                    types match
                        case Nil     => s"""  def $valName: Signal0 = Signal0(self, "$godotName")"""
                        case List(a) =>
                            s"""  def $valName: Signal1[$a] = Signal1(self, "$godotName")"""
                        case List(a, b) =>
                            s"""  def $valName: Signal2[$a, $b] = Signal2(self, "$godotName")"""
                        case List(a, b, c) =>
                            s"""  def $valName: Signal3[$a, $b, $c] = Signal3(self, "$godotName")"""
                        case _ => ""
                    end match
                end signalDef
                val defs = sigs.map { case (_, _, sn, ts) => signalDef(sn, ts) }.filter(_.nonEmpty)
                    .mkString("\n")
                if defs.nonEmpty then s"extension (self: $cls)\n$defs" else ""
            }.filter(_.nonEmpty).mkString("\n\n")
            if blocks.nonEmpty then
                os.write.over(
                  signalsFile,
                  s"""// Generated by RegistrationScan — do not edit.
                    |package $sigPkg
                    |
                    |import com.julianavar.gdext.core.{Signal0, Signal1, Signal2, Signal3, GodotObject}
                    |import com.julianavar.gdext.core.{ToVariant, FromVariant, given}
                    |import com.julianavar.gdext.generated.{*, given}
                    |
                    |$blocks
                    |""".stripMargin
                )
                out += PathRef(signalsFile)
            end if
        end if

        if found.nonEmpty then
            val pkg         = found.head._1
            val fingerprint = found.map { case (p, c) => s"$p.$c" }.mkString(",").hashCode
                .toHexString
            val calls = found.map { case (p, c) =>
                val fqn = if p.nonEmpty then s"$p.$c" else c
                s"        Register.auto[$fqn]()"
            }.mkString("\n")
            os.write.over(
              regFile,
              s"""// Generated by RegistrationScan — do not edit. Fingerprint: $fingerprint
                |package $pkg
                |
                |import com.julianavar.gdext.core.Register
                |import com.julianavar.gdext.generated.{*, given}
                |
                |object GeneratedRegistrations:
                |    def registerAll(): Unit =
                |$calls
                |""".stripMargin
            )
            out += PathRef(regFile)

            if !userHasEntry then
                os.write.over(
                  entryFile,
                  s"""// Generated by RegistrationScan — do not edit. Fingerprint: $fingerprint
                    |package $pkg
                    |
                    |import com.julianavar.gdext.core.*
                    |import _root_.scala.scalanative.unsafe.*
                    |
                    |object GeneratedEntry:
                    |    @exported("godot_scala_init")
                    |    def godotScalaInit(
                    |        getProcAddress: GetProcAddressFn,
                    |        library:        Ptr[Byte],
                    |        initPtr:        Ptr[GdxInitStruct]
                    |    ): CUnsignedChar =
                    |        GeneratedRegistrations.registerAll()
                    |        com.julianavar.gdext.GodotEntry.init(getProcAddress, library, initPtr)
                    |    end godotScalaInit
                    |end GeneratedEntry
                    |""".stripMargin
                )
                out += PathRef(entryFile)
            end if
        else if !userHasEntry then
            // No @gdclass classes but no user entry either — emit a bare entry.
            // Derive the package from the module directory path (last two segments).
            val pkg = moduleDir.segments.toSeq.takeRight(2).mkString(".")
            os.write.over(
              entryFile,
              s"""// Generated by RegistrationScan — do not edit.
                |package $pkg
                |
                |import com.julianavar.gdext.core.*
                |import _root_.scala.scalanative.unsafe.*
                |
                |object GeneratedEntry:
                |    @exported("godot_scala_init")
                |    def godotScalaInit(
                |        getProcAddress: GetProcAddressFn,
                |        library:        Ptr[Byte],
                |        initPtr:        Ptr[GdxInitStruct]
                |    ): CUnsignedChar =
                |        com.julianavar.gdext.GodotEntry.init(getProcAddress, library, initPtr)
                |    end godotScalaInit
                |end GeneratedEntry
                |""".stripMargin
            )
            out += PathRef(entryFile)
        end if

        out.toSeq
    }
end GodotScalaNativeModule
