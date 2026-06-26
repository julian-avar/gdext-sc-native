package gdext.core

import scala.scalanative.libc.stdlib.malloc
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

    /** Unsafe downcast — rewraps `ptr` as T via GodotClass[T].wrap.
      *
      * No runtime type check is performed. Use when you know the actual Godot class of the node.
      * {{{
      * val btn = $"Button".as[Button]
      * btn.setModulate(Color(1f, 0.5f, 0.5f, 1f))
      * }}}
      */
    def as[T <: GodotObject](using gc: GodotClass[T]): T = gc.wrap(ptr)

    /** Connect a Godot signal to a Scala closure.
      *
      * The closure may freely capture `this` and any other Scala state. Godot Native's non-moving
      * GC keeps captured objects alive for as long as the connection exists.
      *
      * {{{
      * btn.connect("pressed") { () =>
      *   label.text = "Clicked!"
      * }
      * }}}
      */
    def connect(signal: String)(cb: () => Unit): Unit = Zone {
        val id    = CallbackRegistry.register(cb)
        val udBuf = malloc(4).asInstanceOf[Ptr[Int]]
        !udBuf = id
        GdxApi.connectSignal(
          ptr,
          toCString(signal),
          CallbackRegistry.trampoline,
          udBuf.asInstanceOf[Ptr[Byte]]
        )
    }
end GodotObject
