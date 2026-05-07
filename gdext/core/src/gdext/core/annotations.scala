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

class `export`     extends StaticAnnotation
class export_range extends StaticAnnotation

class onready extends StaticAnnotation

class icon extends StaticAnnotation

class static_unload extends StaticAnnotation

class tool extends StaticAnnotation
