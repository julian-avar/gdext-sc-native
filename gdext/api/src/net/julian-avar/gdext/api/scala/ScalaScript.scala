package net.`julian-avar`.gdext.scala

import scala.scalanative.unsafe.*
import net.`julian-avar`.gdext
import gdext.core.*
import gdext.core.virtual.VirtualEntry
import gdext.generated.*

class ScalaScript(_p: Ptr[Byte] = null) extends ScriptExtension(_p):
    // These 3 keep Godot's underscore-prefixed name: ScriptExtension's ancestor
    // Script already declares a real public canInstantiate()/hasSourceCode()/reload() method, so
    // the generator falls back instead of colliding with it (see resolveVirtualScalaName in the
    // generator). Being "paired" virtuals, they also require `(using CanCallApi)` — satisfied
    // automatically here since this file lives inside the `gdext` package tree.
    override def isValid(): Boolean                                 = true
    override def _canInstantiate()(using CanCallApi): Boolean       = false
    override def editorCanReloadFromFile(): Boolean                 = true
    override def _hasSourceCode()(using CanCallApi): Boolean        = false
    override def _reload(keepState: Boolean)(using CanCallApi): Int = 0

    override def getLanguage(): ScriptLanguage = ScalaScriptLanguageSupport.langInstance
end ScalaScript

object ScalaScript:
    val entries: Vector[VirtualEntry] = Vector(
      VirtualEntry(
        "_get_instance_base_type",
        required = true,
        dispatch = (_, _, ret) => GdxApi.initStringName(ret, c"Node")
      ),
      VirtualEntry(
        "_get_source_code",
        required = true,
        dispatch = (_, _, ret) => GdxApi.initGodotString(ret, c"")
      ),
      VirtualEntry(
        "_get_global_name",
        required = true,
        dispatch = (_, _, ret) => GdxApi.initStringName(ret, c"")
      )
    ) ++ ScriptExtensionVirtuals.entries
end ScalaScript
