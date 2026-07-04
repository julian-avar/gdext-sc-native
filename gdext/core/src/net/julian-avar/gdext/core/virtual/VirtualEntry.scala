package net.`julian-avar`.gdext.core.virtual

import net.`julian-avar`.gdext.core.GodotObject
import scala.scalanative.unsafe.*

/** Describes one virtual method on a Godot class.
  *
  * `dispatch` is called by ClassRegistrar's get_virtual_func trampoline whenever Godot invokes this
  * virtual on an extension instance. It receives the Scala object, the raw argument array, and the
  * return-value buffer exactly as Godot hands them to a CallVirtualFn.
  *
  * `scalaName` is the Scala-facing override name the generator chose for this virtual (the logical
  * Symbol name, not source syntax): Godot's name with its leading underscore stripped (e.g. "ready"
  * for "_ready"), or -- if that would collide with a same-class or inherited regular method, a
  * JVM/AnyRef member, or a reserved Scala word -- Godot's own underscore- prefixed name instead
  * (e.g. "_getFormats", since CameraFeed also has its own public "getFormats()"). Users write those
  * rare collision cases with Godot's own underscore-prefixed name as-is (e.g.
  * `override def _getFormats()`) -- a leading underscore is a perfectly valid plain Scala
  * identifier, no escaping needed. `Register.auto` matches a user class's declared `override`
  * methods against this field to detect which virtuals it overrides. Left as "" (unused) for
  * hand-written entries that don't correspond to a user-overridable method.
  *
  * When the underscore-prefixed name is kept because of a collision with a genuine PUBLIC sibling
  * method (the "paired" case, e.g. `_getFormats` vs. `getFormats()` -- as opposed to a
  * JVM/reserved-word collision, which has no public sibling) the generated stub also requires an
  * extra `(using CanCallApi)` parameter list. This makes calling the override point directly from
  * outside the `gdext` package tree a compile error, rather than relying on naming convention (or
  * the old, leaky `protected[gdext]`) to discourage it -- see `CanCallApi`.
  */
case class VirtualEntry(
    name: String,
    required: Boolean,
    dispatch: (GodotObject, Ptr[Ptr[Byte]], Ptr[Byte]) => Unit,
    scalaName: String = ""
)
