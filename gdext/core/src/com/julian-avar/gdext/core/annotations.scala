package com.`julian-avar`.gdext.core

import scala.annotation.StaticAnnotation

/** Marks a class as a Godot extension class. Signal handles are auto-generated as extension methods
  * by the `GodotScalaNativeModule` scanner — one `def signalName: SignalN[...]` per
  * `@signal case class`, emitted into `GeneratedSignalHandles.scala` in `generatedSources`.
  */
class gdclass extends StaticAnnotation

/** Marks a method as callable from GDScript. The method name is snake_cased for Godot. */
class func extends StaticAnnotation

/** Marks a var field for export to the Godot inspector. Equivalent to GDScript's @export.
  *
  * An optional `ExportHint` overrides the hint/hintString that the field type's `ExportType`
  * instance would produce:
  * {{{
  * @gdexport(ExportHint.range(0, 100, 1)) var health = 100
  * @gdexport(ExportHint.enum("A,B,C"))    var mode   = 0
  * @gdexport(ExportHint.multiline)        var notes  = ""
  * }}}
  */
class gdexport(val hint: ExportHint = ExportHint.none) extends StaticAnnotation

class `export`                                                   extends StaticAnnotation
class export_range                                               extends StaticAnnotation
class export_enum                                                extends StaticAnnotation
class export_flags                                               extends StaticAnnotation
class export_file                                                extends StaticAnnotation
class export_dir                                                 extends StaticAnnotation
class export_global_file                                         extends StaticAnnotation
class export_global_dir                                          extends StaticAnnotation
class export_multiline                                           extends StaticAnnotation
class export_placeholder                                         extends StaticAnnotation
class export_color_no_alpha                                      extends StaticAnnotation
class export_node_path                                           extends StaticAnnotation
class export_group(val name: String, val prefix: String = "")    extends StaticAnnotation
class export_subgroup(val name: String, val prefix: String = "") extends StaticAnnotation
class export_category(val name: String)                          extends StaticAnnotation
class export_storage                                             extends StaticAnnotation
class export_custom                                              extends StaticAnnotation
class export_tool_button                                         extends StaticAnnotation

class onready extends StaticAnnotation

/** Declares an inner case class as a Godot signal on an extension class.
  *
  * The case class name is snake_cased to become the signal name. Parameters (future) will become
  * typed signal arguments. Example:
  * {{{
  * @signal case class died()
  * @signal case class hit(damage: Int)
  * }}}
  * Emit via `emitSignal("died")` on the containing object.
  */
class signal extends StaticAnnotation

/** Marks a parameterless Scala 3 enum as a Godot enum for `@gdexport` fields.
  *
  * Only annotated enums are exported; non-annotated enums stay private to Scala.
  * {{{
  * @gdenum
  * enum MoveState:
  *   case Idle, Walking, Running, Jumping, Dead
  *
  * @gdexport var state: MoveState = MoveState.Idle
  * }}}
  */
class gdenum extends StaticAnnotation

class rpc extends StaticAnnotation

class warning_ignore extends StaticAnnotation

class icon extends StaticAnnotation

class static_unload extends StaticAnnotation

class tool extends StaticAnnotation
