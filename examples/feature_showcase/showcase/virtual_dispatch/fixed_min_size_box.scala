package example.feature_showcase.showcase.virtual_dispatch

import com.`julian-avar`.gdext.core.*
import com.`julian-avar`.gdext.generated.*

/** Two kinds of Godot virtual overrides, and how to tell them apart
  *
  * Most virtuals are pure lifecycle notifications with no public counterpart -- `_ready`,
  * `_process`, `_physics_process` and friends -- so their Scala names simply drop the leading
  * underscore: `ready()`, `process()`, `physicsProcess()`. See `ShowcaseSc.ready()` /
  * `PlayerSc.physicsProcess()` for that (common) case.
  *
  * A minority of virtuals are the OTHER HALF of an existing public method, not a standalone
  * notification. Per Godot's own class reference
  * (https://docs.godotengine.org/en/stable/classes/class_control.html),
  * `_get_minimum_size()` exists so a custom `Control` can report its own minimum size --
  * Godot's C++ implementation of the *public* `get_minimum_size()` (the method any layout
  * `Container` actually calls) invokes this override to get the real value. Stripping the
  * leading underscore here would collide with `Control`'s own existing `getMinimumSize()`, so
  * the generator keeps Godot's own name instead: `_getMinimumSize()`.
  *
  * Unlike C# -- whose bindings mark every virtual override `public`, relying on the underscore
  * prefix alone (naming convention) to signal "don't call this directly" -- this scheme makes
  * that guarantee a compile error for paired virtuals specifically: the generated stub also
  * requires a `(using CanCallApi)` parameter list, so only the engine's own generated
  * dispatch code (which lives inside the `gdext` package tree, where the evidence is available)
  * can call it. Code outside `gdext` has no `CanCallApi` in scope and no way to construct one
  * (the trait is `sealed`), so calling it directly is always a compile error. Pure-lifecycle
  * virtuals need no such gate -- there's no public sibling to guard against confusion with, so
  * they're plain public methods, same as C#.
  *
  * The takeaway: overriding a "paired" virtual like this one is an ADVANCED, comparatively rare
  * thing to do -- only relevant if you're implementing your OWN custom Control/Texture2D/
  * Resource/Mesh/etc. subclass. Ordinary game code (the overwhelming majority of `@gdclass`
  * usage) never touches these virtuals at all; it only ever calls the plain public method
  * (`getMinimumSize()`, `getWidth()`, `getRid()`, ...). See `ShowcaseSc.ready()` for a live
  * demonstration that calling the public method returns exactly what this override supplies.
  *
  * `_getMinimumSize` isn't a one-off -- every paired virtual follows the same shape. This class
  * also overrides `_getTooltip`, the paired virtual behind `Control.getTooltip()`, to show the
  * pattern generalizes: same `(using CanCallApi)` gate, same relationship to its public sibling.
  *
  * For Control specifically, `get_minimum_size()`'s C++ implementation is a pure passthrough to
  * this virtual -- there's no extra combination logic in the wrapper (the custom_minimum_size
  * clamping Control also does lives in a separate, non-virtual get_combined_minimum_size(), which
  * has no paired virtual at all). That's the general shape of Godot's own paired-virtual
  * convention: the public wrapper exists specifically to call the override, so it's always a 1:1
  * delegation -- another reason there's no legitimate case for calling `_getMinimumSize()`
  * directly instead of `getMinimumSize()`.
  */
@gdclass class FixedMinSizeBox extends Control:
    // "_getMinimumSize" keeps Godot's own underscore-prefixed name -- this IS the override
    // point. The `(using CanCallApi)` clause is what actually keeps ordinary calling code
    // from reaching it directly (a compile error, not just convention) -- only a Control
    // subclass overrides it; everyone else just calls getMinimumSize().
    override def _getMinimumSize()(using CanCallApi): Vector2 = Vector2(120f, 40f)

    // Second paired virtual, same shape: Control's own public getTooltip() calls into this
    // override to build the string a hovering mouse sees. See ShowcaseSc.ready() for the
    // matching call to the public getTooltip().
    override def _getTooltip(atPosition: Vector2)(using CanCallApi): String =
        s"Fixed-size box (${atPosition.x.toInt}, ${atPosition.y.toInt})"
end FixedMinSizeBox

object FixedMinSizeBox:
    given GodotClass[FixedMinSizeBox] = GodotClass.derived[FixedMinSizeBox]
