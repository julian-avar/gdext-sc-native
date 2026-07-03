package com.`julian-avar`.gdext.core

/** Typed handle for a Godot signal with no arguments.
  *
  * Declared as a `lazy val` on the owning class, initialized from the `@signal` case class:
  * {{{
  * @signal case class died()
  * lazy val died: Signal0 = Signal0(this, "died")
  * }}}
  */
final class Signal0(private val host: GodotObject, val name: String):
    def emitSignal(): Unit = GdxApi.emitSignal(host.ptr, name)

    def connect(f: () => Unit): ConnectionToken  = host.connect(name)(f)
    def disconnect(token: ConnectionToken): Unit = host.disconnect(token)
end Signal0

/** Typed handle for a Godot signal with one argument.
  * {{{
  * @signal case class scored(points: Int)
  * lazy val scored: Signal1[Int] = Signal1(this, "scored")
  * // Emit:
  * scored.emitSignal(100)
  * // Connect:
  * node.scored.connect { pts => updateUI(pts) }
  * }}}
  */
final class Signal1[A](private val host: GodotObject, val name: String)(using
    tv: ToVariant[A],
    fv: FromVariant[A]
):
    def emitSignal(a: A): Unit                   = host.emitSignal[A](name, a)
    def connect(f: A => Unit): ConnectionToken   = host.connect[A](name)(f)
    def disconnect(token: ConnectionToken): Unit = host.disconnect(token)
end Signal1

/** Typed handle for a Godot signal with two arguments.
  * {{{
  * @signal case class healthChanged(newValue: Int, delta: Int)
  * lazy val health_changed: Signal2[Int, Int] = Signal2(this, "health_changed")
  * // Emit:
  * health_changed.emitSignal(_health, _health - prev)
  * // Connect:
  * node.health_changed.connect { (hp, delta) => ... }
  * }}}
  */
final class Signal2[A, B](private val host: GodotObject, val name: String)(using
    tva: ToVariant[A],
    tvb: ToVariant[B],
    fva: FromVariant[A],
    fvb: FromVariant[B]
):
    def emitSignal(a: A, b: B): Unit                = host.emitSignal[A, B](name, a, b)
    def connect(f: (A, B) => Unit): ConnectionToken = host.connect[A, B](name)(f)
    def disconnect(token: ConnectionToken): Unit    = host.disconnect(token)
end Signal2

/** Typed handle for a Godot signal with three arguments. */
final class Signal3[A, B, C](private val host: GodotObject, val name: String)(using
    tva: ToVariant[A],
    tvb: ToVariant[B],
    tvc: ToVariant[C],
    fva: FromVariant[A],
    fvb: FromVariant[B],
    fvc: FromVariant[C]
):
    def emitSignal(a: A, b: B, c: C): Unit             = host.emitSignal[A, B, C](name, a, b, c)
    def connect(f: (A, B, C) => Unit): ConnectionToken = host.connect[A, B, C](name)(f)
    def disconnect(token: ConnectionToken): Unit       = host.disconnect(token)
end Signal3
