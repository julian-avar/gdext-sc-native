package gdext.core.method

import gdext.core.GodotObject
import scala.scalanative.unsafe.*

/** Describes one callable method registered with Godot's ClassDB.
  *
  * `name` is the Godot-side method name (e.g. `"_on_button_pressed"`). `dispatch` receives the
  * Scala object, the raw Variant arg array, and the arg count.
  */
case class MethodEntry(name: String, dispatch: (GodotObject, Ptr[Ptr[Byte]], Long) => Unit)
