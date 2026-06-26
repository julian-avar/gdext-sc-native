package gdext.core.method

import gdext.core.GodotObject
import scala.scalanative.unsafe.*

/** Describes one callable method registered with Godot's ClassDB.
  *
  * `name` is the Godot-side method name (e.g. `"get_score"`). `dispatch` receives the Scala object,
  * the raw Variant arg array, arg count, and the return-value buffer (`rReturn`). For void methods
  * set `hasReturnValue = false` and ignore `rReturn` in dispatch.
  */
case class MethodEntry(
    name: String,
    dispatch: (GodotObject, Ptr[Ptr[Byte]], Long, Ptr[Byte]) => Unit,
    hasReturnValue: Boolean = false,
    returnVariantType: Int = 0,
    argumentCount: Int = 0
)

object MethodEntry:
    /** Convenience constructor for void (no-return) methods. */
    def void(name: String, dispatch: (GodotObject, Ptr[Ptr[Byte]], Long) => Unit): MethodEntry =
        MethodEntry(name, (obj, args, count, _) => dispatch(obj, args, count))
end MethodEntry
