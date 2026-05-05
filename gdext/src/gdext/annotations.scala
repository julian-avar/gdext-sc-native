package gdext

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
