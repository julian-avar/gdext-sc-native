package gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*
import gdext.generated.Callable

/** Factory methods for creating Godot Callables from Scala lambdas.
  *
  * Use `Callable.lambda` to wrap a Scala closure for engine APIs that take a `Callable`
  * (e.g. `Tween.tween_callback`, `SceneTree.create_timer`, custom signals).
  *
  * {{{
  * // Tween callback:
  * tween.tweenCallback(Callable.lambda { state = Dead })
  *
  * // 1-arg signal connection via engine method:
  * node.connectSignalMethod("health_changed", Callable.lambda[Int] { hp => updateHp(hp) })
  * }}}
  *
  * **Lifetime:** `Callable.lambda` allocates a heap Callable. Godot holds a reference while it
  * has the callable (e.g. inside a Tween). For fire-once callables this is effectively
  * self-managing. For long-lived connections prefer `GodotObject.connect` which provides a
  * `ConnectionToken` for explicit cleanup.
  */
object CallableLambda:
    private def make(id: Int): Callable =
        val udBuf       = malloc(4).asInstanceOf[Ptr[Int]]
        !udBuf = id
        val callableBuf = malloc(16)
        memset(callableBuf, 0, 16.toUSize)
        GdxApi.buildCallable(callableBuf, udBuf.asInstanceOf[Ptr[Byte]])
        new Callable(callableBuf)
    end make

    def apply(f: () => Unit): Callable =
        make(CallbackRegistry.register(new Callback0(f)))

    def apply[A: FromVariant](f: A => Unit): Callable =
        make(CallbackRegistry.register(new Callback1[A](f, summon[FromVariant[A]])))

    def apply[A: FromVariant, B: FromVariant](f: (A, B) => Unit): Callable =
        make(CallbackRegistry.register(
          new Callback2[A, B](f, summon[FromVariant[A]], summon[FromVariant[B]])
        ))

    def apply[A: FromVariant, B: FromVariant, C: FromVariant](f: (A, B, C) => Unit): Callable =
        make(CallbackRegistry.register(
          new Callback3[A, B, C](f, summon[FromVariant[A]], summon[FromVariant[B]], summon[FromVariant[C]])
        ))
end CallableLambda
