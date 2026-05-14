package example.scripting

import scala.scalanative.unsafe.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*
import gdext.core.*
import gdext.core.virtual.VirtualEntry
import gdext.generated.*

/** Holds the live ScalaScriptLanguage instance so ScalaScript can reference it from _get_language.
  * Populated during onSceneInit, after ClassRegistrar.register() completes.
  */
object ScalaScriptLanguageSupport:
    var langInstance: ScalaScriptLanguage = null

/** GDExtension ScriptLanguageExtension implementation for Scala.
  *
  * Registered as extension class "ScalaScriptLanguage" extending "ScriptLanguageExtension". After
  * registration, one instance is constructed and passed to Engine.register_script_language so Godot
  * shows "Scala" in the Attach Script dialog.
  *
  * Key behaviour:
  *   - _can_make_function → false: signal connections ONLY write the [connection] line in the .tscn
  *     file; Godot never tries to insert a function stub into the .scala source.
  *   - _create_script → constructs a fresh ScalaScript extension object.
  *   - _get_name / _get_type / _get_extension are handled by custom VirtualEntry objects (see
  *     companion object) because the code-generated dispatch does not write String values to r_ret
  *     — the custom entries call GdxApi.initGodotString directly.
  */
class ScalaScriptLanguage(_p: Ptr[Byte] = null) extends ScriptLanguageExtension(_p):
    // Permanent StringName buffer for "ScalaScript" — allocated once, never freed.
    private var scalaScriptSN: Ptr[Byte] = null

    private def ensureScalaScriptSN(): Ptr[Byte] =
        if scalaScriptSN == null then
            scalaScriptSN = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
            memset(scalaScriptSN, 0, StringNameSize)
            GdxApi.initStringName(scalaScriptSN, c"ScalaScript")
        end if
        scalaScriptSN
    end ensureScalaScriptSN

    // ── ScriptLanguage overrides ──────────────────────────────────────────────

    override def _hasNamedClasses(): Boolean       = false
    override def _supportsBuiltinMode(): Boolean   = false
    override def _canMakeFunction(): Boolean       = false
    override def _isUsingTemplates(): Boolean      = false
    override def _canInheritFromFile(): Boolean    = false
    override def _supportsDocumentation(): Boolean = false

    /** Returns a new ScalaScript object via ClassDB so it goes through ClassRegistrar's createFn
      * and is properly linked into instanceMap.
      */
    override def _createScript(): gdext.generated.Object =
        new gdext.generated.Object(GdxApi.constructObject(ensureScalaScriptSN()))
end ScalaScriptLanguage

object ScalaScriptLanguage:

    /** Custom VirtualEntry objects that correctly write Godot String values to the r_ret buffer.
      *
      * The generated ScriptLanguageExtensionVirtuals dispatch for CString-returning methods calls
      * the Scala method but silently discards the return value without writing to r_ret. Prepending
      * these entries shadows the generated ones so the linear scan in get_virtual_call_data_func
      * finds the fixed version first.
      */
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
      ),
    ) ++ ScriptLanguageExtensionVirtuals.entries
end ScalaScriptLanguage
