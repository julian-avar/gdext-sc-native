package gdext.godot

import scala.scalanative.unsafe.*
import gdext.generated.Vector2

object Predef:
    extension (v: Ptr[Vector2])
        inline def *(scalar: Float): Ptr[Vector2] =
            val result = stackalloc[Vector2]()
            result.x = v.x * scalar
            result.y = v.y * scalar
            result
    end extension
end Predef
