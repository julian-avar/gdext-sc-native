package example.feature_showcase.showcase.memory_mangement

import com.`julian-avar`.gdext.core.*
import com.`julian-avar`.gdext.generated.*
import scala.scalanative.unsafe.Zone

/** Explicit Zone scopes for value-type returns
  *
  * Generated methods that **return** value types (Vector2, Color, Transform2D, …) allocate the
  * result in a Zone arena instead of on the local stack, because the returned pointer must remain
  * valid across multiple statements.
  *
  * Wrap each group of value-type calls in a `Zone { … }` block. The Zone is deterministic and
  * cheap: it stack-allocates a small arena and frees it when the block exits. The caller does not
  * need to think about individual allocations — just enter a Zone when you need value-type results.
  *
  * This tier disappears entirely once Phase 2 lands and value-builtins become copy-based Scala
  * value types.
  */
@gdclass class MouseTracker extends Node2D:
    @gdexport var color = Color(1f, 1f, 1f, 1f)
    @gdexport var width = 2f

    override def _draw(): Unit = Zone:
        // getGlobalMousePosition and toLocal return Vector2 —
        // the Zone provides the arena for their return buffers.
        val mouse = getGlobalMousePosition()
        val here  = toLocal(mouse)

        // drawLine accepts Vector2 and Color by pointer — no Zone
        // needed for the call itself. The Zone-allocated values
        // from above are still live.
        drawLine(Vector2(0f, 0f), here, color)

    @func def getTargetPosition(): Vector2 = Zone:
        getGlobalMousePosition()
end MouseTracker
