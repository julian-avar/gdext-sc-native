package millbuild

import mill.*
import mill.scalalib.*
import mill.scalalib.publish.*
import mill.api.BuildCtx
import mill.scalanativelib.*
import mill.scalalib.scalafmt.ScalafmtModule

import millbuild.Config

object Config:
    import utils.codeberg

    val version      = "0.1.0"
    val millVersion  = "1.1.2"
    val godotVersion = "4.7.0"
    // val godotVersions      = Seq("4.0.0", "4.1.0", "4.2.0", "4.3.0", "4.4.0", "4.5.0", "4.6.1", "4.7.0")
    val godotVersions      = Seq("4.5.0", "4.6.1", "4.7.0")
    val scalaVersion       = "3.8.4"
    val scalaNativeVersion = "0.5.11"
    val jvmVersion         = "21"
    val pomSettings        = PomSettings(
      description = "GD Extension Scala",
      organization = "net.julian-avar",
      url = "https://codeberg.org/julian-avar/gdext-scala-native",
      licenses = Seq(License.`GPL-3.0`),
      versionControl = VersionControl.codeberg("julian-avar", "gdext-scala-native"),
      developers = Seq(Developer("julian-avar", "Julian Avar", "https://codeberg.org/julian-avar"))
    )
end Config

object Deps:
    object Versions:
        val scalameta = "4.17.0"
        val mill      = Config.millVersion

    val upickle = mvn"com.lihaoyi::upickle::4.4.2"
    val pprint  = mvn"com.lihaoyi::pprint::0.9.6"
    val osLib   = mvn"com.lihaoyi::os-lib::0.11.8"

    val scalameta              = mvn"org.scalameta::scalameta:${Versions.scalameta}"
    val trees                  = mvn"org.scalameta::trees:4.17.0"
    val scalaXml               = mvn"org.scala-lang.modules::scala-xml:2.3.0"
    val millLibs               = mvn"com.lihaoyi::mill-libs:${Versions.mill}"
    val millLibsScalanativelib = mvn"com.lihaoyi::mill-libs-scalanativelib:${Versions.mill}"
    val millScalafixPlugin     = mvn"com.goyeau::mill-scalafix::0.6.0"
end Deps

object utils:
    extension (vc: VersionControl.type)
        def codeberg(owner: String, repo: String, tag: Option[String] = None): VersionControl =
            VersionControl(
              browsableRepository = Some(s"https://codeberg.org/$owner/$repo"),
              connection =
                  Some(VersionControlConnection.gitGit("codeberg.org", s"$owner/$repo.git")),
              developerConnection = Some(
                VersionControlConnection
                    .gitSsh("codeberg.org", s":$owner/$repo.git", username = Some("git"))
              ),
              tag = tag
            )
    end extension
end utils

trait SharedPublishedModule extends PublishModule:
    def publishVersion = Config.version
    def pomSettings    = Config.pomSettings

trait SharedModule extends ScalaModule with ScalafmtModule with SharedPublishedModule:
    def scalaVersion  = Config.scalaVersion
    def scalacOptions = Seq(
      "-explain",
      "-Wconf:msg=julian-avar.*will be encoded on the classpath:s"
      // "-Wconf:msg=Implicit parameters should be provided with a `using` clause:s",
      // "-rewrite",
      // "-source:3.8"
    )

    // Not published anywhere yet -- this just gets `docJar` (required by Sonatype Central
    // alongside the sources jar) producing a real, populated site once descriptions start
    // flowing in from the generator, instead of an empty shell.
    def scalaDocOptions = Task {
        super.scalaDocOptions() ++ Seq(
          "-project-version",
          Config.version,
          "-source-links:" + s"${BuildCtx.workspaceRoot}=${Config.pomSettings
                  .url}/src/branch/main€{FILE_PATH}.€{FILE_EXT}#L€{FILE_LINE}",
          "-siteroot",
          (BuildCtx.workspaceRoot / "docs").toString
        )
    }
end SharedModule

trait SharedMillPluginModule extends SharedModule:
    def millVersion    = Config.millVersion
    def platformSuffix = "_mill1"

trait SharedNativeModule extends SharedModule with ScalaNativeModule:
    def scalaNativeVersion = Config.scalaNativeVersion
    // Use the clang set in environment variables (default to clang/clang++)
    def nativeClang   = Task.Input { PathRef(os.Path(Task.env.getOrElse("CC", "clang"))) }
    def nativeClangPP = Task.Input { PathRef(os.Path(Task.env.getOrElse("CXX", "clang++"))) }
end SharedNativeModule
