package net.`julian-avar`.gdext.scala

import scala.scalanative.unsafe.*
import net.`julian-avar`.gdext
import gdext.core.*
import gdext.core.virtual.VirtualEntry
import gdext.generated.*

/** Lets Godot save a `ScalaScript` resource back to its `.scala` file — this is what makes
  * editing the source from within Godot's built-in script editor (Ctrl+S) persist to disk.
  *
  * `_recognize` is left to the generated dispatch (not hand-written): its Resource-typed arg and
  * Boolean return both marshal correctly today, unlike the String-typed virtuals below.
  */
class ScalaScriptResourceFormatSaver(_p: Ptr[Byte] = null) extends ResourceFormatSaver(_p):
    override def recognize(resource: Resource): Boolean = GdClassRegistry
        .lookupByPtr(resource.ptr).exists(_.isInstanceOf[ScalaScript])
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
        "_save",
        required = false,
        dispatch = (_, args, ret) =>
            val resourcePtr = !(args(0).asInstanceOf[Ptr[Ptr[Byte]]])
            val path        = GdxApi.godotStringToScala(args(1))
            val ok = GdClassRegistry.lookupByPtr(resourcePtr) match
                case Some(instance: ScalaScript) =>
                    val file = FileAccess.open(path, 2 /* WRITE */ )
                    if file.ptr != null then
                        file.storeString(instance.sourceText)
                        file.close()
                        true
                    else false
                case _ => false
            !(ret.asInstanceOf[Ptr[Long]]) = (if ok then 0L else 1L) // OK : FAILED
      )
    ) ++ ResourceFormatSaverVirtuals.entries
end ScalaScriptResourceFormatSaver
