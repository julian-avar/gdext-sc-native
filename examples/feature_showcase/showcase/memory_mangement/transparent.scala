package example.feature_showcase.showcase.memory_mangement

import com.julianavar.gdext.core.*
import com.julianavar.gdext.generated.*

/** Transparent stack allocations
  *
  * Every `@gdexport` field and `@func` param/return is a primitive (Int, Long, Double, Float,
  * Boolean, String) or a `stackalloc`'d value type like `Vector2(x, y)` or `Color(r, g, b, a)`. No
  * Zone, no malloc — the generated wrappers handle memory on the local stack automatically.
  *
  * This works the same whether you're using the high-level or low-level generated API — the stack
  * allocation is invisible in both styles.
  */
@gdclass class PureScalaCounter extends Node:
    @gdexport var count  = 0
    @gdexport var speed  = 1.0f
    @gdexport var label  = "counter"
    @gdexport var active = true

    private var accumulator = 0.0f

    override def process(delta: Double): Unit = if active then
        accumulator += delta.toFloat * speed
        if accumulator >= 1.0f then
            count += 1
            accumulator -= 1.0f

    @func def reset(): Unit            = count = 0; accumulator = 0.0f
    @func def isAbove(n: Int): Boolean = count > n
    @func def describe(): String       = s"$label: $count"
end PureScalaCounter
