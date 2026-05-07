package gdext.core.virtual

import gdext.core.GodotObject
import scala.scalanative.unsafe.*

/** Describes one virtual method on a Godot class.
  *
  * `dispatch` is called by ClassRegistrar's get_virtual_func trampoline whenever Godot invokes this
  * virtual on an extension instance. It receives the Scala object, the raw argument array, and the
  * return-value buffer exactly as Godot hands them to a CallVirtualFn.
  */
case class VirtualEntry(
    name: String,
    required: Boolean,
    dispatch: (GodotObject, Ptr[Ptr[Byte]], Ptr[Byte]) => Unit
)
