package net.`julian-avar`.gdext.scala

import scala.scalanative.unsafe.*
import net.`julian-avar`.gdext
import gdext.core.*
import gdext.core.virtual.VirtualEntry
import gdext.generated.*

/** Lets Godot save a `ScalaScript` resource to its `.scala` file — this is what makes the editor's
  * New Script dialog able to create the file on disk, and what makes editing the source from within
  * Godot's built-in script editor (Ctrl+S) persist.
  *
  * `_recognize` is dispatched through the generated entry (Resource arg / Boolean return both
  * marshal correctly); the String-typed virtuals below are hand-written bypasses. In particular
  * `_recognize_path` MUST be hand-written: the generated table advertises it as implemented while
  * its default body returns `false`, and `ResourceSaver::save` (Godot 4.4+) skips any saver whose
  * `recognize_path` rejects the path — which silently disabled saving `.scala` files entirely (the
  * New Script dialog's "Could not create script in filesystem" error).
  */
class ScalaScriptResourceFormatSaver(_p: Ptr[Byte] = null) extends ResourceFormatSaver(_p):
    // Engine-side class check instead of GdClassRegistry.lookupByPtr: argument pointers are not
    // reliably resolvable through the registry maps (unlike virtual calls *on* an object, which
    // the engine routes through the instance binding).
    override def recognize(resource: Resource): Boolean = resource != null &&
        resource.ptr != null && resource.isClass("ScalaScript")
end ScalaScriptResourceFormatSaver

object ScalaScriptResourceFormatSaver:
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
            val path = GdxApi.godotStringToScala(args(1))
            !(ret.asInstanceOf[Ptr[Byte]]) = (if path.endsWith(".scala") then 1 else 0).toByte
      ),
      VirtualEntry(
        "_set_uid",
        required = false,
        dispatch = (_, args, ret) =>
            val path = GdxApi.godotStringToScala(args(0))
            val uid  = !(args(1).asInstanceOf[Ptr[Long]])
            // Persist the UID as a `<path>.uid` sidecar, the way GDScript's saver does for
            // script files (which have nowhere to embed a UID).
            val file = FileAccess.open(path + ".uid", 2 /* WRITE */ )
            val ok   =
                if file != null && file.ptr != null then
                    file.storeString(ResourceUID.idToText(uid))
                    file.close()
                    true
                else false
            !(ret.asInstanceOf[Ptr[Long]]) = (if ok then 0L else 1L) // OK : FAILED
      ),
      VirtualEntry(
        "_save",
        required = false,
        dispatch = (_, args, ret) =>
            val resourcePtr = !(args(0).asInstanceOf[Ptr[Ptr[Byte]]])
            val path        = GdxApi.godotStringToScala(args(1))
            // Fetch the source through the engine (Script::get_source_code roundtrips into our
            // _get_source_code on the correct instance) instead of GdClassRegistry.lookupByPtr,
            // which can miss argument pointers.
            val ok =
                if resourcePtr == null then false
                else
                    val source = summon[GodotClass[Script]].wrap(resourcePtr).getSourceCode()
                    val file   = FileAccess.open(path, 2 /* WRITE */ )
                    if file != null && file.ptr != null then
                        file.storeString(if source != null then source else "")
                        file.close()
                        true
                    else false
                    end if
            !(ret.asInstanceOf[Ptr[Long]]) = (if ok then 0L else 1L) // OK : FAILED
      )
    ) ++ ResourceFormatSaverVirtuals.entries
end ScalaScriptResourceFormatSaver
