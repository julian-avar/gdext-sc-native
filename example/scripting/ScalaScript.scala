package example.scripting

import scala.scalanative.unsafe.*
import gdext.core.*
import gdext.core.virtual.VirtualEntry
import gdext.generated.*

/** GDExtension ScriptExtension implementation for Scala.
  *
  * Registered as extension class "ScalaScript" extending "ScriptExtension". Instances are created
  * by ScalaScriptLanguage._createScript() via ClassDB so they go through ClassRegistrar's normal
  * create/free lifecycle.
  *
  * This implementation is intentionally minimal: Scala is compiled externally, so the script object
  * is a thin marker that tells Godot "this node has a Scala script". The node shows a script
  * indicator in the editor, and existing signal connections in the .tscn file resolve against the
  * compiled extension class at runtime.
  *
  * CString-returning virtual methods (_get_source_code, _get_instance_base_type, _get_global_name)
  * are handled by custom VirtualEntry objects in the companion object, for the same reason as
  * ScalaScriptLanguage: the generated dispatch does not write to r_ret for these return types.
  */
class ScalaScript(_p: Ptr[Byte] = null) extends ScriptExtension(_p):
    override def _isValid(): Boolean                 = true
    override def _canInstantiate(): Boolean          = false
    override def _editorCanReloadFromFile(): Boolean = true
    override def _hasSourceCode(): Boolean           = false
    override def _reload(keepState: Boolean): Int    = 0 // Error.OK

    /** Returns the language singleton so Godot's editor knows which language owns this script. */
    override def _getLanguage(): ScriptLanguage = ScalaScriptLanguageSupport.langInstance
end ScalaScript

object ScalaScript:
    /** Custom VirtualEntry objects that write Godot String / StringName values to r_ret.
      *
      * Same generator-bug workaround as in ScalaScriptLanguage: prepend these before
      * ScriptExtensionVirtuals.entries so the linear scan finds them first.
      *
      * _get_instance_base_type: "Node" is a safe generic default. Godot uses this to determine
      * which node types the script is compatible with in the Attach Script dialog.
      *
      * _get_global_name / _get_source_code: empty is the correct sentinel (no global class name, no
      * in-memory source — the real source lives on disk and is compiled externally).
      */
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
