package gdext.core

import scala.scalanative.libc.stdlib.{malloc, free}
import scala.scalanative.libc.string.memset
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

/** Returned by `connect` — can be passed to `disconnect` to remove the connection.
  *
  * Stores the signal name and the heap-allocated userdata pointer that identifies the callback in
  * Godot's custom Callable. Both fields must remain valid until `disconnect`.
  */
final class ConnectionToken private[core] (val signal: String, private[core] val udBuf: Ptr[Byte])

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

    // ── Signal emission ──────────────────────────────────────────────────────
    // Note: the generated Object class provides `emitSignal(signal: String): Int`
    // (GDScript-parity; returns error code). Use the generated method for the 0-arg case.
    // Signal typed handles (Signal0/1/2/3) route through GdxApi directly.

    /** Emit a signal with one typed argument. */
    def emitSignal[A: ToVariant](name: String, a: A): Unit =
        val va = stackalloc[Byte](24)
        memset(va, 0, 24.toUSize)
        summon[ToVariant[A]].write(va, a)
        GdxApi.emitSignalArgs1(ptr, name, va)
    end emitSignal

    /** Emit a signal with two typed arguments. */
    def emitSignal[A: ToVariant, B: ToVariant](name: String, a: A, b: B): Unit =
        val va = stackalloc[Byte](24); val vb = stackalloc[Byte](24)
        memset(va, 0, 24.toUSize); memset(vb, 0, 24.toUSize)
        summon[ToVariant[A]].write(va, a)
        summon[ToVariant[B]].write(vb, b)
        GdxApi.emitSignalArgs2(ptr, name, va, vb)
    end emitSignal

    /** Emit a signal with three typed arguments. */
    def emitSignal[A: ToVariant, B: ToVariant, C: ToVariant](name: String, a: A, b: B, c: C): Unit =
        val va = stackalloc[Byte](24); val vb = stackalloc[Byte](24); val vc = stackalloc[Byte](24)
        memset(va, 0, 24.toUSize); memset(vb, 0, 24.toUSize); memset(vc, 0, 24.toUSize)
        summon[ToVariant[A]].write(va, a)
        summon[ToVariant[B]].write(vb, b)
        summon[ToVariant[C]].write(vc, c)
        GdxApi.emitSignalArgs3(ptr, name, va, vb, vc)
    end emitSignal

    // ── Signal connection ────────────────────────────────────────────────────
    //
    // `connect` is overloaded by closure arity; Scala 3 resolves via function type.
    // Explicit type params `connect[Node]("body_entered") { n => ... }` select the 1-arg form.
    // Returns a ConnectionToken to pass to `disconnect` when the connection is no longer needed.

    /** Connect a signal to a zero-arg Scala closure. */
    def connect(signal: String)(cb: () => Unit): ConnectionToken =
        val id    = CallbackRegistry.register(new Callback0(cb))
        val udBuf = malloc(4).asInstanceOf[Ptr[Int]]
        !udBuf = id
        Zone {
            GdxApi.connectSignal(
              ptr,
              toCString(signal),
              CallbackRegistry.trampoline,
              udBuf.asInstanceOf[Ptr[Byte]]
            )
        }
        new ConnectionToken(signal, udBuf.asInstanceOf[Ptr[Byte]])
    end connect

    /** Connect a signal to a typed 1-arg Scala closure.
      * {{{
      * this.connect[Int]("health_changed") { hp => updateHpBar(hp) }
      * healthComponent.connect[Int]("died") { _ => println("dead") }
      * }}}
      */
    def connect[A: FromVariant](signal: String)(cb: A => Unit): ConnectionToken =
        val id    = CallbackRegistry.register(new Callback1[A](cb, summon[FromVariant[A]]))
        val udBuf = malloc(4).asInstanceOf[Ptr[Int]]
        !udBuf = id
        Zone {
            GdxApi.connectSignal(
              ptr,
              toCString(signal),
              CallbackRegistry.trampoline,
              udBuf.asInstanceOf[Ptr[Byte]]
            )
        }
        new ConnectionToken(signal, udBuf.asInstanceOf[Ptr[Byte]])
    end connect

    /** Connect a signal to a typed 2-arg Scala closure. */
    def connect[A: FromVariant, B: FromVariant](signal: String)(
        cb: (A, B) => Unit
    ): ConnectionToken =
        val id = CallbackRegistry
            .register(new Callback2[A, B](cb, summon[FromVariant[A]], summon[FromVariant[B]]))
        val udBuf = malloc(4).asInstanceOf[Ptr[Int]]
        !udBuf = id
        Zone {
            GdxApi.connectSignal(
              ptr,
              toCString(signal),
              CallbackRegistry.trampoline,
              udBuf.asInstanceOf[Ptr[Byte]]
            )
        }
        new ConnectionToken(signal, udBuf.asInstanceOf[Ptr[Byte]])
    end connect

    /** Connect a signal to a typed 3-arg Scala closure. */
    def connect[A: FromVariant, B: FromVariant, C: FromVariant](signal: String)(
        cb: (A, B, C) => Unit
    ): ConnectionToken =
        val id = CallbackRegistry.register(new Callback3[A, B, C](
          cb,
          summon[FromVariant[A]],
          summon[FromVariant[B]],
          summon[FromVariant[C]]
        ))
        val udBuf = malloc(4).asInstanceOf[Ptr[Int]]
        !udBuf = id
        Zone {
            GdxApi.connectSignal(
              ptr,
              toCString(signal),
              CallbackRegistry.trampoline,
              udBuf.asInstanceOf[Ptr[Byte]]
            )
        }
        new ConnectionToken(signal, udBuf.asInstanceOf[Ptr[Byte]])
    end connect

    // Legacy named variants kept for backward compat:
    inline def connect1[A: FromVariant](signal: String)(cb: A => Unit): ConnectionToken =
        connect[A](signal)(cb)
    inline def connect2[A: FromVariant, B: FromVariant](signal: String)(
        cb: (A, B) => Unit
    ): ConnectionToken = connect[A, B](signal)(cb)
    inline def connect3[A: FromVariant, B: FromVariant, C: FromVariant](signal: String)(
        cb: (A, B, C) => Unit
    ): ConnectionToken = connect[A, B, C](signal)(cb)

    /** Disconnect a signal connection. Frees the trampoline allocation. Always call this in
      * `_exitTree` for cross-node connections.
      */
    def disconnect(token: ConnectionToken): Unit =
        GdxApi.disconnectSignal(ptr, token.signal, token.udBuf)
        free(token.udBuf)

end GodotObject
