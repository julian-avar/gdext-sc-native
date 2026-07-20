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
    /** Skeleton seeded into a newly-created `.scala` script, in Godot's template-marker form:
      * `_CLASS_`, `_BASE_` and `_TS_` are substituted by `_make_template` — the same markers
      * Godot's own script templates use, so user-provided editor templates work too. It matches
      * the shape of the manually-written examples, including the imports required for `@gdclass`
      * and the base-class type to resolve when the file is compiled on the next build. The
      * `package` line is load-bearing: the build-time scanner emits its registration file in the
      * first scanned class's package and references classes by FQN — a packageless class would
      * produce an unparseable `package ` header and be unreferenceable from packaged code.
      */
    private val builtinTemplateContent: String =
        s"""package scripts
           |
           |import net.`julian-avar`.gdext.api.*
           |import net.`julian-avar`.gdext.generated.*
           |
           |@gdclass class _CLASS_ extends _BASE_:
           |
           |_TS_override def ready(): Unit =
           |_TS__TS_()
           |
           |end _CLASS_
           |""".stripMargin

    /** Substitute Godot's script-template markers into `content`. */
    private def substituteMarkers(
        content: String,
        className: String,
        baseClassName: String
    ): String =
        val base = if baseClassName.nonEmpty then baseClassName else "Node"
        content.replace("_CLASS_", sanitizeClassName(className)).replace("_BASE_", base)
            .replace("_TS_", "    ")
    end substituteMarkers

    /** For languages without named classes the dialog passes the FILE BASENAME as `class_name`
      * (snake_case, e.g. `my_button`); convert it to a valid PascalCase Scala identifier
      * (`MyButton`), mirroring the manual file↔class convention used by the examples
      * (`scala_script_example_sc.scala` ↔ `ScalaScriptExampleSc`). Manual scan instead of a regex
      * for the same Scala Native reason as `ScalaScript.parseClassName`.
      */
    private def sanitizeClassName(raw: String): String =
        val sb        = new StringBuilder
        var upperNext = true
        for ch <- raw do
            if ch.isLetterOrDigit then
                sb.append(if upperNext then ch.toUpper else ch)
                upperNext = false
            else upperNext = true
        end for
        val name = sb.toString
        if name.isEmpty then "MyClass" else if name.head.isDigit then "Class" + name else name
    end sanitizeClassName

    private def initEmptyArray(buf: Ptr[Byte]): Unit =
        if GdxApi.arrayDefaultCtorPtr != null then
            val ctor = CFuncPtr
                .fromPtr[CFuncPtr2[Ptr[Byte], Ptr[Ptr[Byte]], Unit]](GdxApi.arrayDefaultCtorPtr)
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
            val template      = GdxApi.godotStringToScala(args(0))
            val className     = GdxApi.godotStringToScala(args(1))
            val baseClassName = GdxApi.godotStringToScala(args(2))
            val godotPtr      = GdxApi.constructObject(self.ensureScalaScriptSN())
            val script        = summon[GodotClass[Script]].wrap(godotPtr)
            // The dialog passes the selected template's content (built-in or user-provided);
            // fall back to ours when empty. Engine roundtrip (Script::set_source_code → our
            // _set_source_code) fills the source and resolvedClassName on the correct instance,
            // with no registry lookup that could silently miss and hand the dialog an empty
            // script.
            val content = if template.nonEmpty then template else builtinTemplateContent
            script.setSourceCode(substituteMarkers(content, className, baseClassName))
            // The return slot is a Ref[Script]: Godot copies it into the caller's Ref and then
            // destroys the temporary, so hand the script over with one reference taken or it
            // gets freed on the spot (same reason ScalaScriptBootstrap.activateLanguage calls
            // reference() on the loader/saver before registering them).
            script.reference()
            !(ret.asInstanceOf[Ptr[Ptr[Byte]]]) = godotPtr
      ),
      VirtualEntry(
        "_get_built_in_templates",
        required = true,
        dispatch = (_, args, ret) =>
            initEmptyArray(ret)
            // The dialog queries this once per ancestor of the chosen base class; answer only
            // the root "Object" query so the single Default template appears exactly once.
            // StringNames are interned, so comparing the 8-byte values suffices.
            val queriedSN = !(args(0).asInstanceOf[Ptr[Ptr[Byte]]])
            val objectSN  = !(StringNames.cached("Object").asInstanceOf[Ptr[Ptr[Byte]]])
            if queriedSN == objectSN then
                val d = GdDict[String, String]()
                d("inherit") = "Object"
                d("name") = "Default"
                d("description") = "Scala @gdclass script (class goes live on the next build)"
                d("content") = builtinTemplateContent
                // The engine reads these two as ints; Variant coerces the strings.
                d("id") = "0"
                d("origin") = "0" // TEMPLATE_BUILT_IN
                GdxApi.appendDictionaryToArray(ret, d)
                d.destroy()
            end if
      )
    ) ++ ScriptLanguageExtensionVirtuals.entries
end ScalaScriptLanguage
