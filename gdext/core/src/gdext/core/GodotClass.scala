package gdext

import scala.scalanative.unsafe.*

/** Base trait for all user-defined Godot classes.
  * `ptr` is set by the framework after construction; never pass it manually.
  */
trait GodotClass:
    var ptr: Ptr[Byte]                       = null
    def _ready(): Unit                       = ()
    def _process(delta: Double): Unit        = ()
    def _physicsProcess(delta: Double): Unit = ()
end GodotClass
