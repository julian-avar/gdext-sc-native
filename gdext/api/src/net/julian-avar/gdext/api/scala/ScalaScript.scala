package net.`julian-avar`.gdext.scala

import scala.scalanative.unsafe.*
import net.`julian-avar`.gdext
import gdext.core.*
import gdext.core.virtual.VirtualEntry
import gdext.generated.*

class ScalaScript(_p: Ptr[Byte] = null) extends ScriptExtension(_p):
    // The class name parsed out of `sourceText` (via the same `@gdclass\s+class\s+(\w+)`
    // convention the build-time scanner uses). Empty until a source is loaded/set.
    private[scala] var resolvedClassName: String = ""
    private[scala] var sourceText: String        = ""

    // These 3 keep Godot's underscore-prefixed name: ScriptExtension's ancestor
    // Script already declares a real public canInstantiate()/hasSourceCode()/reload() method, so
    // the generator falls back instead of colliding with it (see resolveVirtualScalaName in the
    // generator). Being "paired" virtuals, they also require `(using CanCallApi)` — satisfied
    // automatically here since this file lives inside the `gdext` package tree.
    override def isValid(): Boolean = true

    // Dynamic: true once `resolvedClassName` names a class that's actually been compiled and
    // registered (via @gdclass). Until then (script written but not yet rebuilt), Godot falls
    // back to a placeholder instance instead of calling _instance_create — see Object::set_script.
    override def _canInstantiate()(using CanCallApi): Boolean =
        GdClassRegistry.getRegistrations.exists(_.name == resolvedClassName)

    override def editorCanReloadFromFile(): Boolean = true

    override def _hasSourceCode()(using CanCallApi): Boolean = sourceText.nonEmpty

    override def _reload(keepState: Boolean)(using CanCallApi): Int =
        resolvedClassName = ScalaScript.parseClassName(sourceText).getOrElse("")
        0 // OK
    end _reload

    override def getLanguage(): ScriptLanguage = ScalaScriptLanguageSupport.langInstance
end ScalaScript

object ScalaScript:
    /** Parse the `@gdclass class <Name>` declaration out of a `.scala` source, mirroring the
      * build-time scanner's `@gdclass\s+class\s+(\w+)` convention (`GodotScalaNativeModule`).
      * Implemented as a manual scan rather than `scala.util.matching.Regex` to avoid depending on
      * Scala Native's regex support at extension runtime.
      */
    def parseClassName(text: String): Option[String] =
        val marker = "@gdclass"
        val idx    = text.indexOf(marker)
        if idx < 0 then None
        else
            var i = idx + marker.length
            while i < text.length && text(i).isWhitespace do i += 1
            if !text.startsWith("class", i) then None
            else
                i += "class".length
                while i < text.length && text(i).isWhitespace do i += 1
                val start = i
                while i < text.length && (text(i).isLetterOrDigit || text(i) == '_') do i += 1
                if i > start then Some(text.substring(start, i)) else None
            end if
        end if
    end parseClassName

    val entries: Vector[VirtualEntry] = Vector(
      VirtualEntry(
        "_get_instance_base_type",
        required = true,
        dispatch = (_, _, ret) => GdxApi.initStringName(ret, c"Node")
      ),
      VirtualEntry(
        "_get_source_code",
        required = true,
        dispatch = (obj, _, ret) =>
            Zone { GdxApi.initGodotString(ret, toCString(obj.asInstanceOf[ScalaScript].sourceText)) }
      ),
      VirtualEntry(
        "_set_source_code",
        required = true,
        dispatch = (obj, args, _) =>
            val self = obj.asInstanceOf[ScalaScript]
            self.sourceText = GdxApi.godotStringToScala(args(0))
            self.resolvedClassName = parseClassName(self.sourceText).getOrElse("")
      ),
      VirtualEntry(
        "_get_global_name",
        required = true,
        dispatch = (_, _, ret) => GdxApi.initStringName(ret, c"")
      ),
      VirtualEntry(
        "_instance_create",
        required = true,
        dispatch = (obj, args, ret) =>
            val self         = obj.asInstanceOf[ScalaScript]
            val forObjectPtr = !(args(0).asInstanceOf[Ptr[Ptr[Byte]]])
            val languagePtr  =
                if ScalaScriptLanguageSupport.langInstance != null then
                    ScalaScriptLanguageSupport.langInstance.ptr
                else null
            val instancePtr = GdClassRegistry.getRegistrations
                .find(_.name == self.resolvedClassName) match
                case Some(reg) => ScriptInstanceRegistrar
                        .createInstance(reg, forObjectPtr, self.ptr, languagePtr)
                case None => ScriptInstanceRegistrar
                        .createPlaceholder(languagePtr, self.ptr, forObjectPtr)
            !(ret.asInstanceOf[Ptr[Ptr[Byte]]]) = instancePtr
      )
    ) ++ ScriptExtensionVirtuals.entries
end ScalaScript
