package net.`julian-avar`.gdext.mill
package godotscalanativelib.resource_parser

import scala.util.matching.Regex
import scala.xml.{NodeSeq, XML}

/** Parses Godot's `doc/classes` XML (vendored by `GeneratorModule.vendorDocClasses`) into
  * ready-to-emit Scaladoc text, translating Godot's BBCode-like markup into Scaladoc's wiki
  * syntax. Downstream generator code only ever sees plain, already-translated strings.
  */
object DocParser:
    case class DocClass(
        name: String,
        briefDescription: Option[String],
        description: Option[String],
        methods: Map[String, String], // method name -> description
        properties: Map[String, String], // member name -> description
        constants: Map[String, String] // constant name -> description
    )

    /** Parses every `*.xml` file in `docClassesDir`, keyed by Godot class name. A missing
      * directory (a Godot version that hasn't had docs vendored yet, via `vendorDocClasses`)
      * yields an empty map rather than failing the build.
      */
    def parse(docClassesDir: os.Path): Map[String, DocClass] =
        if !os.exists(docClassesDir) then Map.empty
        else
            os.list(docClassesDir).filter(_.ext == "xml").flatMap(parseFile)
                .map(c => c.name -> c).toMap
    end parse

    private def parseFile(file: os.Path): Option[DocClass] =
        try
            val xml  = XML.loadFile(file.toIO)
            val name = (xml \ "@name").text

            val methods = (xml \ "methods" \ "method").flatMap { m =>
                textOpt(m \ "description").map((m \ "@name").text -> _)
            }.toMap

            val properties = (xml \ "members" \ "member").flatMap { m =>
                textOpt(m).map((m \ "@name").text -> _)
            }.toMap

            val constants = (xml \ "constants" \ "constant").flatMap { c =>
                textOpt(c).map((c \ "@name").text -> _)
            }.toMap

            Some(DocClass(
              name = name,
              briefDescription = textOpt(xml \ "brief_description"),
              description = textOpt(xml \ "description"),
              methods = methods,
              properties = properties,
              constants = constants
            ))
        catch
            case e: Exception =>
                System.err.println(s"  warning: failed to parse doc xml $file: ${e.getMessage}")
                None
    end parseFile

    private def textOpt(nodes: NodeSeq): Option[String] =
        val raw = nodes.text.trim
        if raw.isEmpty then None else Some(bbcodeToScaladoc(raw))

    // ── BBCode -> Scaladoc wiki markup ─────────────────────────────────────────

    private val codeblocksTag = """(?s)\[codeblocks\](.*?)\[/codeblocks\]""".r
    private val gdscriptTag   = """(?s)\[gdscript(?:\s+[^\]]*)?\](.*?)\[/gdscript\]""".r
    private val csharpTag     = """(?s)\[csharp(?:\s+[^\]]*)?\](.*?)\[/csharp\]""".r
    private val codeblockTag  = """(?s)\[codeblock(?:\s+[^\]]*)?\](.*?)\[/codeblock\]""".r
    private val codeTag       = """(?s)\[code(?:\s+[^\]]*)?\](.*?)\[/code\]""".r
    private val boldTag       = """(?s)\[b\](.*?)\[/b\]""".r
    private val italicTag     = """(?s)\[i\](.*?)\[/i\]""".r
    private val underlineTag  = """(?s)\[u\](.*?)\[/u\]""".r
    private val centerTag     = """(?s)\[center\](.*?)\[/center\]""".r
    private val kbdTag        = """(?s)\[kbd\](.*?)\[/kbd\]""".r
    private val colorTag      = """(?s)\[color=[^\]]*\](.*?)\[/color\]""".r
    private val urlWithHref   = """(?s)\[url=([^\]]*)\](.*?)\[/url\]""".r
    private val bareUrl       = """(?s)\[url\](.*?)\[/url\]""".r
    private val xrefTag       =
        """\[(?:method|member|signal|constant|enum|param|theme_item)\s+([^\]]+)\]""".r
    private val bareClassRef  = """\[([A-Z][A-Za-z0-9_]*)\]""".r
    // Godot's own primitive Variant types are referenced in lowercase (e.g. "unlike [float] which
    // is always 64-bit"); bareClassRef only catches the capitalized class-name form.
    private val primitiveRef  = """\[(int|float|bool|void)\]""".r
    private val leftoverTag   = """(?<!\[)\[/?[a-zA-Z_][a-zA-Z0-9_]*(?:=[^\]]*)?\](?!\])""".r

    /** `$DOCS_URL` is Godot's own unresolved template placeholder for its docs-site base URL, not
      * a real link -- left as-is, Scaladoc tries to resolve it as a member reference and warns.
      */
    private val docsUrlBase = "https://docs.godotengine.org/en/stable"

    /** Translates a raw Godot doc string (BBCode-like markup) into Scaladoc wiki-syntax text,
      * matching the hand-written house style: prose + `'''bold'''`/`''italic''`/`` `code` `` /
      * `{{{ }}}` blocks, no `@param`/`@return` tags.
      */
    def bbcodeToScaladoc(raw: String): String =
        var text = raw
            .replace("[lb]", "[").replace("[rb]", "]").replace("$DOCS_URL", docsUrlBase)

        text = codeblocksTag.replaceAllIn(
          text,
          m => Regex.quoteReplacement(codeBlockOf(m.group(1)))
        )
        text = codeblockTag.replaceAllIn(text, m => Regex.quoteReplacement(codeBlockOf(m.group(1))))
        text = codeTag.replaceAllIn(text, m => Regex.quoteReplacement(s"`${m.group(1)}`"))
        text = boldTag.replaceAllIn(text, m => Regex.quoteReplacement(s"'''${m.group(1)}'''"))
        text = italicTag.replaceAllIn(text, m => Regex.quoteReplacement(s"''${m.group(1)}''"))
        text = underlineTag.replaceAllIn(text, m => Regex.quoteReplacement(m.group(1)))
        text = centerTag.replaceAllIn(text, m => Regex.quoteReplacement(m.group(1)))
        text = kbdTag.replaceAllIn(text, m => Regex.quoteReplacement(s"`${m.group(1)}`"))
        text = colorTag.replaceAllIn(text, m => Regex.quoteReplacement(m.group(1)))
        text = urlWithHref.replaceAllIn(
          text,
          m => Regex.quoteReplacement(s"[[${m.group(1)} ${m.group(2)}]]")
        )
        text = bareUrl.replaceAllIn(text, m => Regex.quoteReplacement(m.group(1)))
        text = xrefTag.replaceAllIn(text, m => Regex.quoteReplacement(s"`${m.group(1)}`"))
        text = primitiveRef.replaceAllIn(text, m => Regex.quoteReplacement(s"`${m.group(1)}`"))
        // Not all referenced classes are generated in this same module/scope (e.g. hand-written
        // core types referenced from api-layer docs), so `[[Name]]` Scaladoc cross-links often
        // fail to resolve -- render as inline code instead, same as [method]/[member]/etc. above.
        text = bareClassRef.replaceAllIn(text, m => Regex.quoteReplacement(s"`${m.group(1)}`"))
        text = text.replace("[br]", "\n")
        text = leftoverTag.replaceAllIn(text, "")

        // Godot prose occasionally contains a literal comment-delimiter sequence (e.g. a path with
        // a wildcard); neutralize it so it can't prematurely close the Scaladoc block comment this
        // text gets embedded in.
        text = text.replace("/*", "/ *").replace("*/", "* /")

        trimProseLines(text).trim
    end bbcodeToScaladoc

    private val codeSpan = """(?s)\{\{\{.*?\}\}\}""".r

    /** The XML source indents every line of prose to match its nesting depth; that indentation is
      * meaningless once flattened into a Scaladoc line and would otherwise show up as stray
      * leading whitespace on every continuation line. Strips it from prose while leaving
      * `\{\{\{ \}\}\}` code blocks alone -- `codeBlockOf` already dedented those relative to each
      * other, and a blanket per-line trim here would flatten their internal structure.
      */
    private def trimProseLines(text: String): String =
        val sb   = StringBuilder()
        var last = 0
        for m <- codeSpan.findAllMatchIn(text) do
            sb.append(tidyProse(text.substring(last, m.start)))
            sb.append(m.matched)
            last = m.end
        sb.append(tidyProse(text.substring(last)))
        sb.toString
    end trimProseLines

    private def tidyProse(s: String): String = s.linesIterator.map(_.trim).mkString("\n")

    /** Picks the GDScript variant out of a `[codeblocks]` tab group (falling back to the C# one,
      * then the raw content), and wraps it as a Scaladoc `{{{ }}}` block with common indentation
      * stripped.
      */
    private def codeBlockOf(inner: String): String =
        val chosen = gdscriptTag.findFirstMatchIn(inner).map(_.group(1))
            .orElse(csharpTag.findFirstMatchIn(inner).map(_.group(1))).getOrElse(inner)
        s"{{{${dedent(chosen)}}}}"
    end codeBlockOf

    private def dedent(s: String): String =
        val lines     = s.stripSuffix("\n").linesIterator.toVector
        val nonBlank  = lines.filter(_.trim.nonEmpty)
        val minIndent = if nonBlank.isEmpty then 0
        else nonBlank.map(l => l.takeWhile(_.isWhitespace).length).min
        lines.map(l => if l.length >= minIndent then l.drop(minIndent) else l.trim)
            .mkString("\n").trim
    end dedent
end DocParser
