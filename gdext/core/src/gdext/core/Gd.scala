package gdext

import scala.scalanative.unsafe.*

/** Typed reference to a Godot engine object.
  *
  * `Gd[Node]` is what you hold when you receive an engine object from a method call. It is
  * distinct from your own class instance, which extends the generated wrapper class directly.
  *
  * Internally just a raw pointer — zero runtime overhead.
  *
  * {{{
  * val btn: Gd[Button] = findChild("Button").get
  * btn.setDisabled(true)
  * }}}
  */
opaque type Gd[+T <: GodotObject] = Ptr[Byte]

object Gd:
  /** Wrap a raw Godot object pointer. Internal use by generated code. */
  private[gdext] inline def apply[T <: GodotObject](ptr: Ptr[Byte]): Gd[T] = ptr

  /** The null reference. Prefer `Option[Gd[T]]` at API boundaries. */
  inline def Null[T <: GodotObject]: Gd[T] = null.asInstanceOf[Ptr[Byte]]

  extension [T <: GodotObject](self: Gd[T])
    inline def isNull: Boolean                       = (self: Ptr[Byte]) == null
    inline def toOpt: Option[Gd[T]]                  = if (self: Ptr[Byte]) == null then None else Some(self)
    inline def rawPtr: Ptr[Byte]                     = self
    inline def widen[U >: T <: GodotObject]: Gd[U]  = self
