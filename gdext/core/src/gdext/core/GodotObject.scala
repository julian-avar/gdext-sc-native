package gdext

import scala.scalanative.unsafe.*

/** Base class for all Godot objects — both generated engine wrappers and user-defined extension
  * classes.
  *
  * `ptr` is set by the framework after construction; do not use it in your constructor body.
  *
  * Override virtual lifecycle methods as needed:
  * {{{
  * @gdclass
  * class MyNode extends Node:
  *   override def _ready(): Unit = GdxApi.printString("Hello!")
  * }}}
  */
abstract class GodotObject:
    var ptr: Ptr[Byte] = null

    // Lifecycle virtuals — will move to the generated Node class once the @gdclass macro lands.
    def _ready(): Unit                       = ()
    def _process(delta: Double): Unit        = ()
    def _physicsProcess(delta: Double): Unit = ()
end GodotObject

/** Backward-compatibility alias. Generated code and existing call sites still compile without
  * regenerating. Will be removed once the generator emits `GodotObject` directly.
  */
type GodotClass = GodotObject
