package net.`julian-avar`.gdext.scala

import scala.scalanative.unsafe.*
import net.`julian-avar`.gdext
import gdext.core.*
import gdext.core.virtual.VirtualEntry
import gdext.generated.*

/** Lets Godot load `.scala` files as `Script` resources — without this, the editor's Attach
  * Script dialog has nothing to save/load a `.scala` file through at all. All virtuals here are
  * hand-written bypasses rather than relying on the generated dispatch: String-typed virtual
  * arguments/returns are silently discarded by the generator today (see `ScalaScript.scala`'s
  * doc comment on the same issue for `_instance_create`).
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
            GdClassRegistry.lookupByPtr(godotPtr).foreach { instance =>
                val script = instance.asInstanceOf[ScalaScript]
                script.sourceText = FileAccess.getFileAsString(path)
                script.resolvedClassName = ScalaScript.parseClassName(script.sourceText)
                    .getOrElse("")
            }
            !(ret.asInstanceOf[Ptr[Ptr[Byte]]]) = godotPtr
      )
    ) ++ ResourceFormatLoaderVirtuals.entries
end ScalaScriptResourceFormatLoader
