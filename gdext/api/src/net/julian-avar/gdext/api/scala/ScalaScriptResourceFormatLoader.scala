package net.`julian-avar`.gdext.scala

import scala.scalanative.unsafe.*
import net.`julian-avar`.gdext
import gdext.core.*
import gdext.core.virtual.VirtualEntry
import gdext.generated.*

/** Lets Godot load `.scala` files as `Script` resources — without this, the editor has nothing to
  * load a `.scala` file through at all. All virtuals here are hand-written bypasses rather than
  * relying on the generated dispatch: String-typed virtual arguments/returns are silently discarded
  * by the generator today (see `ScalaScript.scala`'s doc comment on the same issue for
  * `_instance_create`).
  *
  * `_recognize_path`, `_exists` and `_get_resource_uid` in particular MUST be hand-written: the
  * generated table advertises them as implemented while their default bodies return `false`/`0`,
  * and `ResourceLoader` gates every load on `recognize_path` — which silently disabled loading
  * `.scala` files entirely.
  */
class ScalaScriptResourceFormatLoader(_p: Ptr[Byte] = null) extends ResourceFormatLoader(_p)

object ScalaScriptResourceFormatLoader:
    val entries: Vector[VirtualEntry] = Vector(
      VirtualEntry(
        "_get_recognized_extensions",
        required = false,
        dispatch = (_, _, ret) =>
            GdxApi.initPackedStringArray(ret)
            GdxApi.packedStringArrayAppendCString(ret, c"scala")
      ),
      VirtualEntry(
        "_recognize_path",
        required = false,
        dispatch = (_, args, ret) =>
            val path = GdxApi.godotStringToScala(args(0))
            !(ret.asInstanceOf[Ptr[Byte]]) = (if path.endsWith(".scala") then 1 else 0).toByte
      ),
      VirtualEntry(
        "_exists",
        required = false,
        dispatch = (_, args, ret) =>
            val path = GdxApi.godotStringToScala(args(0))
            !(ret.asInstanceOf[Ptr[Byte]]) = (if FileAccess.fileExists(path) then 1 else 0).toByte
      ),
      VirtualEntry(
        "_get_resource_uid",
        required = false,
        dispatch = (_, args, ret) =>
            val path    = GdxApi.godotStringToScala(args(0))
            val uidPath = path + ".uid"
            // Read back the `<path>.uid` sidecar written by the saver's `_set_uid`;
            // -1 (ResourceUID's INVALID_ID) tells the editor to assign a fresh one.
            val id =
                if FileAccess.fileExists(uidPath) then
                    val text = FileAccess.getFileAsString(uidPath)
                    if text != null && text.trim.nonEmpty then ResourceUID.textToId(text.trim)
                    else -1L
                else -1L
            !(ret.asInstanceOf[Ptr[Long]]) = id
      ),
      VirtualEntry(
        "_get_resource_type",
        required = false,
        dispatch = (_, args, ret) =>
            val path   = GdxApi.godotStringToScala(args(0))
            val result = if path.endsWith(".scala") then "Script" else ""
            Zone { GdxApi.initGodotString(ret, toCString(result)) }
      ),
      VirtualEntry(
        "_handles_type",
        required = false,
        dispatch = (_, args, ret) =>
            val typeName = GdxApi.godotStringToScala(args(0))
            val matches  = typeName == "Script"
            !(ret.asInstanceOf[Ptr[Byte]]) = (if matches then 1 else 0).toByte
      ),
      VirtualEntry(
        "_load",
        required = true,
        dispatch = (_, args, ret) =>
            val path     = GdxApi.godotStringToScala(args(0))
            val godotPtr = GdxApi.constructObject(StringNames.cached("ScalaScript"))
            // Engine roundtrip (Script::set_source_code → our _set_source_code) fills the
            // source and resolvedClassName on the correct instance, with no registry lookup
            // that could silently miss and yield an empty script.
            summon[GodotClass[Script]].wrap(godotPtr)
                .setSourceCode(FileAccess.getFileAsString(path))
            // The return slot is a Variant, not a bare object pointer: build a proper OBJECT
            // Variant (whose constructor also takes the RefCounted reference that keeps the
            // script alive once the engine unwraps it).
            GdxApi.buildObjectVariant(ret, godotPtr)
      )
    ) ++ ResourceFormatLoaderVirtuals.entries
end ScalaScriptResourceFormatLoader
