package net.`julian-avar`.gdext.scala

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
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
    override def isUsingTemplates(): Boolean      = true
    override def canInheritFromFile(): Boolean    = false
    override def supportsDocumentation(): Boolean = false

    override def createScript(): gdext.generated.Object = summon[GodotClass[gdext.generated.Object]]
        .wrap(GdxApi.constructObject(ensureScalaScriptSN()))
end ScalaScriptLanguage

object ScalaScriptLanguage:
    /** Boilerplate seeded into a newly-created `.scala` script from the editor's Attach Script
      * dialog — matches the shape of the manually-written examples elsewhere in this project.
      * `template` (the dialog's selected built-in template body) is intentionally unused: the
      * project returns an empty template list from `_get_built_in_templates`, so Godot passes
      * an empty string and relies on this method to generate the skeleton entirely from
      * `class_name` and `base_class_name`.
      */
    private def defaultTemplate(className: String, baseClassName: String): String =
        val base = if baseClassName.nonEmpty then baseClassName else "Node"
        val name = if className.nonEmpty then className else "MyClass"
        s"""@gdclass class $name extends $base:
           |
           |    override def ready(): Unit =
           |        ()
           |
           |end $name
           |""".stripMargin
    end defaultTemplate

    private def initEmptyArray(buf: Ptr[Byte]): Unit =
        if GdxApi.arrayDefaultCtorPtr != null then
            val ctor = CFuncPtr.fromPtr[CFuncPtr2[Ptr[Byte], Ptr[Ptr[Byte]], Unit]](
                GdxApi.arrayDefaultCtorPtr
            )
            ctor(buf, null)
        end if
    end initEmptyArray

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
      VirtualEntry(
        "_make_template",
        required = true,
        dispatch = (obj, args, ret) =>
            val self          = obj.asInstanceOf[ScalaScriptLanguage]
            val className     = GdxApi.godotStringToScala(args(1))
            val baseClassName = GdxApi.godotStringToScala(args(2))
            val godotPtr      = GdxApi.constructObject(self.ensureScalaScriptSN())
            GdClassRegistry.lookupByPtr(godotPtr).foreach { instance =>
                val script = instance.asInstanceOf[ScalaScript]
                script.sourceText = defaultTemplate(className, baseClassName)
                script.resolvedClassName = if className.nonEmpty then className else ""
            }
            !(ret.asInstanceOf[Ptr[Ptr[Byte]]]) = godotPtr
      ),
      VirtualEntry(
        "_get_built_in_templates",
        required = true,
        dispatch = (_, _, ret) =>
            initEmptyArray(ret)
            // Godot 4.7 editor calls this at startup when isUsingTemplates is true.
            // Return an empty array for now and rely on _make_template.
      )
    ) ++ ScriptLanguageExtensionVirtuals.entries
end ScalaScriptLanguage
