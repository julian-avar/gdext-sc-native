package net.`julian-avar`.gdext.scala

import scala.scalanative.unsafe.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*
import net.`julian-avar`.gdext
import gdext.core.*
import gdext.core.virtual.VirtualEntry
import gdext.generated.*

object ScalaScriptLanguageSupport:
    var langInstance: ScalaScriptLanguage = null

class ScalaScriptLanguage(_p: Ptr[Byte] = null) extends ScriptLanguageExtension(_p):
    private var scalaScriptSN: Ptr[Byte] = null

    private def ensureScalaScriptSN(): Ptr[Byte] =
        if scalaScriptSN == null then
            scalaScriptSN = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
            memset(scalaScriptSN, 0, StringNameSize)
            GdxApi.initStringName(scalaScriptSN, c"ScalaScript")
        end if
        scalaScriptSN
    end ensureScalaScriptSN

    override def hasNamedClasses(): Boolean       = false
    override def supportsBuiltinMode(): Boolean   = false
    override def canMakeFunction(): Boolean       = false
    override def isUsingTemplates(): Boolean      = false
    override def canInheritFromFile(): Boolean    = false
    override def supportsDocumentation(): Boolean = false

    override def createScript(): gdext.generated.Object = summon[GodotClass[gdext.generated.Object]]
        .wrap(GdxApi.constructObject(ensureScalaScriptSN()))
end ScalaScriptLanguage

object ScalaScriptLanguage:
    val entries: Vector[VirtualEntry] = Vector(
      VirtualEntry(
        "_get_name",
        required = true,
        dispatch = (_, _, ret) => GdxApi.initGodotString(ret, c"Scala")
      ),
      VirtualEntry(
        "_get_type",
        required = true,
        dispatch = (_, _, ret) => GdxApi.initGodotString(ret, c"Scala")
      ),
      VirtualEntry(
        "_get_extension",
        required = true,
        dispatch = (_, _, ret) => GdxApi.initGodotString(ret, c"scala")
      ),
      VirtualEntry(
        "_get_recognized_extensions",
        required = true,
        dispatch = (_, _, ret) =>
            GdxApi.initPackedStringArray(ret)
            GdxApi.packedStringArrayAppendCString(ret, c"scala")
      )
    ) ++ ScriptLanguageExtensionVirtuals.entries
end ScalaScriptLanguage
