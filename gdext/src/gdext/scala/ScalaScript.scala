package gdext.scala

import scala.scalanative.unsafe.*
import gdext.core.*
import gdext.core.virtual.VirtualEntry
import gdext.generated.*

class ScalaScript(_p: Ptr[Byte] = null) extends ScriptExtension(_p):
    override def _isValid(): Boolean                 = true
    override def _canInstantiate(): Boolean          = false
    override def _editorCanReloadFromFile(): Boolean = true
    override def _hasSourceCode(): Boolean           = false
    override def _reload(keepState: Boolean): Int    = 0

    override def _getLanguage(): ScriptLanguage = ScalaScriptLanguageSupport.langInstance
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