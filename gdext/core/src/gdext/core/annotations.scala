package gdext.core

import scala.annotation.StaticAnnotation

/** Marks a class as a Godot extension class.
  *
  * The annotation is currently documentation-only. Registration is explicit in the user's entry
  * point. Future: a Mill plugin will auto-generate the registration calls.
  *
  * Example:
  * {{{
  * @gdclass
  * class SpinningCube extends Node3D:
  *   override def _ready(): Unit = ...
  * }}}
  */
class gdclass extends StaticAnnotation

class `export`              extends StaticAnnotation
class export_range          extends StaticAnnotation
class export_enum           extends StaticAnnotation
class export_flags          extends StaticAnnotation
class export_file           extends StaticAnnotation
class export_dir            extends StaticAnnotation
class export_global_file    extends StaticAnnotation
class export_global_dir     extends StaticAnnotation
class export_multiline      extends StaticAnnotation
class export_placeholder    extends StaticAnnotation
class export_color_no_alpha extends StaticAnnotation
class export_node_path      extends StaticAnnotation
class export_group          extends StaticAnnotation
class export_subgroup       extends StaticAnnotation
class export_category       extends StaticAnnotation
class export_storage        extends StaticAnnotation
class export_custom         extends StaticAnnotation
class export_tool_button    extends StaticAnnotation

class onready extends StaticAnnotation

class rpc extends StaticAnnotation

class warning_ignore extends StaticAnnotation

class icon extends StaticAnnotation

class static_unload extends StaticAnnotation

class tool extends StaticAnnotation
