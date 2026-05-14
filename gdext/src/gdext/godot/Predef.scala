package gdext.godot

import scala.scalanative.unsafe.*
import gdext.generated.*

object Predef:
    extension (v: Ptr[Vector2])
        inline def *(scalar: Float): Ptr[Vector2] = v.map(_ * scalar)
        inline def *(o: Ptr[Vector2]): Ptr[Vector2]    = v.combine(o)(_ * _)

        inline def /(scalar: Float): Ptr[Vector2] = v.map(_ / scalar)
        inline def /(o: Ptr[Vector2]): Ptr[Vector2]    = v.combine(o)(_ / _)

        inline def +(scalar: Float): Ptr[Vector2] = v.map(_ + scalar)
        inline def +(o: Ptr[Vector2]): Ptr[Vector2]    = v.combine(o)(_ + _)

        inline def -(scalar: Float): Ptr[Vector2] = v.map(_ - scalar)
        inline def -(o: Ptr[Vector2]): Ptr[Vector2]    = v.combine(o)(_ - _)

        inline def map(f: Float => Float): Ptr[Vector2] =
            val result = stackalloc[Vector2]()
            result.x = f(v.x)
            result.y = f(v.y)
            result
        end map

        inline def combine(o: Ptr[Vector2])(f: (Float, Float) => Float): Ptr[Vector2] =
            val result = stackalloc[Vector2]()
            result.x = f(v.x, o.x)
            result.y = f(v.y, o.y)
            result
        end combine
    end extension

    extension (v: Ptr[Vector2i])
        inline def *(scalar: Int): Ptr[Vector2i] = v.map(_ * scalar)
        inline def *(o: Ptr[Vector2i]): Ptr[Vector2i] = v.combine(o)(_ * _)

        inline def /(scalar: Int): Ptr[Vector2i] = v.map(_ / scalar)
        inline def /(o: Ptr[Vector2i]): Ptr[Vector2i] = v.combine(o)(_ / _)

        inline def +(scalar: Int): Ptr[Vector2i] = v.map(_ + scalar)
        inline def +(o: Ptr[Vector2i]): Ptr[Vector2i] = v.combine(o)(_ + _)

        inline def -(scalar: Int): Ptr[Vector2i] = v.map(_ - scalar)
        inline def -(o: Ptr[Vector2i]): Ptr[Vector2i] = v.combine(o)(_ - _)

        inline def map(f: Int => Int): Ptr[Vector2i] =
            val result = stackalloc[Vector2i]()
            result.x = f(v.x)
            result.y = f(v.y)
            result
        end map

        inline def combine(o: Ptr[Vector2i])(f: (Int, Int) => Int): Ptr[Vector2i] =
            val result = stackalloc[Vector2i]()
            result.x = f(v.x, o.x)
            result.y = f(v.y, o.y)
            result
        end combine
    end extension

    extension (v: Ptr[Vector3])
        inline def *(scalar: Float): Ptr[Vector3] = v.map(_ * scalar)
        inline def *(o: Ptr[Vector3]): Ptr[Vector3]    = v.combine(o)(_ * _)

        inline def /(scalar: Float): Ptr[Vector3] = v.map(_ / scalar)
        inline def /(o: Ptr[Vector3]): Ptr[Vector3]    = v.combine(o)(_ / _)

        inline def +(scalar: Float): Ptr[Vector3] = v.map(_ + scalar)
        inline def +(o: Ptr[Vector3]): Ptr[Vector3]    = v.combine(o)(_ + _)

        inline def -(scalar: Float): Ptr[Vector3] = v.map(_ - scalar)
        inline def -(o: Ptr[Vector3]): Ptr[Vector3]    = v.combine(o)(_ - _)

        inline def map(f: Float => Float): Ptr[Vector3] =
            val result = stackalloc[Vector3]()
            result.x = f(v.x)
            result.y = f(v.y)
            result.z = f(v.z)
            result
        end map

        inline def combine(o: Ptr[Vector3])(f: (Float, Float) => Float): Ptr[Vector3] =
            val result = stackalloc[Vector3]()
            result.x = f(v.x, o.x)
            result.y = f(v.y, o.y)
            result.z = f(v.z, o.z)
            result
        end combine
    end extension

    extension (v: Ptr[Vector3i])
        inline def *(scalar: Int): Ptr[Vector3i] = v.map(_ * scalar)
        inline def *(o: Ptr[Vector3i]): Ptr[Vector3i] = v.combine(o)(_ * _)

        inline def /(scalar: Int): Ptr[Vector3i] = v.map(_ / scalar)
        inline def /(o: Ptr[Vector3i]): Ptr[Vector3i] = v.combine(o)(_ / _)

        inline def +(scalar: Int): Ptr[Vector3i] = v.map(_ + scalar)
        inline def +(o: Ptr[Vector3i]): Ptr[Vector3i] = v.combine(o)(_ + _)

        inline def -(scalar: Int): Ptr[Vector3i] = v.map(_ - scalar)
        inline def -(o: Ptr[Vector3i]): Ptr[Vector3i] = v.combine(o)(_ - _)

        inline def map(f: Int => Int): Ptr[Vector3i] =
            val result = stackalloc[Vector3i]()
            result.x = f(v.x)
            result.y = f(v.y)
            result.z = f(v.z)
            result
        end map

        inline def combine(o: Ptr[Vector3i])(f: (Int, Int) => Int): Ptr[Vector3i] =
            val result = stackalloc[Vector3i]()
            result.x = f(v.x, o.x)
            result.y = f(v.y, o.y)
            result.z = f(v.z, o.z)
            result
        end combine
    end extension
end Predef
