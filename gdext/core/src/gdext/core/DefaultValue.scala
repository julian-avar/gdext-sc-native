package gdext.core

import scala.scalanative.unsafe.*

/** Provides a sensible zero/empty value for a type, used when Godot needs to construct an
  * extension class instance without arguments.
  *
  * The macro summons `DefaultValue[A]` for each constructor parameter that has no explicit Scala
  * default. Givens are provided for primitives, object references, `Option`, `Tres[T]`, `Tscn[T]`,
  * and `Required[A]`. Math type givens (`Ptr[Vector2]` etc.) live in `gdext.generated.ExportTypes`.
  */
trait DefaultValue[A]:
    def default: A

object DefaultValue:
    inline def of[A](using dv: DefaultValue[A]): A = dv.default

    given DefaultValue[Boolean]:
        def default = false

    given DefaultValue[Int]:
        def default = 0

    given DefaultValue[Long]:
        def default = 0L

    given DefaultValue[Float]:
        def default = 0.0f

    given DefaultValue[Double]:
        def default = 0.0

    given DefaultValue[String]:
        def default = ""

    given defaultGd: [T <: GodotObject] => (GodotClass[T]) => DefaultValue[Gd[T]] =
        new DefaultValue[Gd[T]]:
            def default = Gd.nullOf[T]

    given defaultOption: [A] => DefaultValue[Option[A]] =
        new DefaultValue[Option[A]]:
            def default = None

    // DefaultValue for Tres[T] and Tscn[T] live in their companion objects (opaque type scoping).

    given defaultRequired: [A] => (inner: DefaultValue[A]) => DefaultValue[Required[A]] =
        new DefaultValue[Required[A]]:
            def default = Required(inner.default)
end DefaultValue
