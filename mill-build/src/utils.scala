package millbuild

import mill.*
import mill.scalalib.*
import mill.scalalib.publish.*

object Config:
    import utils.codeberg

    val version            = "0.1.0"
    val millVersion        = "1.1.2"
    val godotVersion       = "4.7.0"
    val godotVersions      = Seq("4.5.0", "4.6.1", "4.7.0")
    val scalaVersion       = "3.8.4"
    val scalaNativeVersion = "0.5.11"
    val jvmVersion         = "21"
    val pomSettings        = PomSettings(
        description = "GD Extension Scala",
        organization = "com.julian-avar",
        url = "https://codeberg.org/bajopiano/gdext-scala-native",
        licenses = Seq(License.`GPL-3.0`),
        versionControl = VersionControl.codeberg("bajopiano", "gdext-scala-native"),
        developers = Seq(Developer("bajopiano", "Bajopiano", "https://codeberg.org/bajopiano"))
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
    val millLibs               = mvn"com.lihaoyi::mill-libs:${Versions.mill}"
    val millLibsScalanativelib = mvn"com.lihaoyi::mill-libs-scalanativelib:${Versions.mill}"
    val millScalafixPlugin = mvn"com.goyeau::mill-scalafix::0.6.0"
end Deps

object utils:
    extension (vc: VersionControl.type)
        def codeberg(owner: String, repo: String, tag: Option[String] = None): VersionControl =
            VersionControl(
            browsableRepository = Some(s"https://codeberg.org/$owner/$repo"),
            connection = Some(VersionControlConnection.gitGit("codeberg.org", s"$owner/$repo.git")),
            developerConnection = Some(
                VersionControlConnection
                    .gitSsh("codeberg.org", s":$owner/$repo.git", username = Some("git"))
            ),
            tag = tag
            )
    end extension
end utils
