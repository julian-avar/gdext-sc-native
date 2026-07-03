package com.`julian-avar`.gdext.core

/** Overrides the inspector hint for a `@gdexport` field.
  *
  * Usage:
  * {{{
  * @gdexport(ExportHint.range(0, 100, 1))   var health = 100
  * @gdexport(ExportHint.enum("A,B,C"))       var mode   = 0
  * @gdexport(ExportHint.file("*.png"))       var icon   = ""
  * @gdexport(ExportHint.resourceType("Mesh")) var mesh  = null.asInstanceOf[Gd[Mesh]]
  * @gdexport(ExportHint.nodeType("Node2D"))   var target = null.asInstanceOf[Gd[Node2D]]
  * @gdexport(ExportHint.multiline)            var desc   = ""
  * @gdexport(ExportHint.colorNoAlpha)         var tint   = Color(1, 1, 1, 1)
  * }}}
  *
  * When present, the hint/hintString from ExportHint replaces whatever the field type's
  * `ExportType` instance would have produced.
  */
sealed trait ExportHint:
    def hint: Int
    def hintString: String

object ExportHint:
    /** No override — use the ExportType's default hint (equivalent to omitting the parameter). */
    case object none extends ExportHint:
        def hint       = PropertyHint.None
        def hintString = ""

    /** Numeric slider: `"min,max"` or `"min,max,step"` or `"min,max,step,extra"`. */
    final case class range(min: Double, max: Double, step: Double = 0.0, suffix: String = "")
        extends ExportHint:
        def hint       = PropertyHint.Range
        def hintString =
            val base = if step == 0.0 then s"$min,$max" else s"$min,$max,$step"
            if suffix.nonEmpty then s"$base,$suffix" else base
    end range

    /** Comma-separated enum labels: `"North,South,East,West"`. */
    final case class `enum`(labels: String) extends ExportHint:
        def hint       = PropertyHint.Enum
        def hintString = labels

    /** File picker with optional filter: `"*.png,*.jpg"`. */
    final case class file(filter: String = "") extends ExportHint:
        def hint       = PropertyHint.File
        def hintString = filter

    /** Directory picker. */
    case object dir extends ExportHint:
        def hint       = PropertyHint.Dir
        def hintString = ""

    /** Global file picker with optional filter. */
    final case class globalFile(filter: String = "") extends ExportHint:
        def hint       = PropertyHint.GlobalFile
        def hintString = filter

    /** Global directory picker. */
    case object globalDir extends ExportHint:
        def hint       = PropertyHint.GlobalDir
        def hintString = ""

    /** Resource picker filtered to `typeName`. */
    final case class resourceType(typeName: String) extends ExportHint:
        def hint       = PropertyHint.ResourceType
        def hintString = typeName

    /** Node picker filtered to `typeName`. */
    final case class nodeType(typeName: String) extends ExportHint:
        def hint       = PropertyHint.NodeType
        def hintString = typeName

    /** Multiline text editor for String fields. */
    case object multiline extends ExportHint:
        def hint       = PropertyHint.MultilineText
        def hintString = ""

    /** Color picker without the alpha channel. */
    case object colorNoAlpha extends ExportHint:
        def hint       = PropertyHint.ColorNoAlpha
        def hintString = ""
end ExportHint
