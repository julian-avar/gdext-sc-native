package gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*

/** A typed wrapper around Godot's `Array` builtin type.
  *
  * `GdArray[A]` is a reference-counted heap-allocated handle (8 bytes). Elements are marshalled
  * through Variant so any type with `ToVariant`/`FromVariant` instances can be stored.
  *
  * Creating a new array:
  * {{{
  * val arr = GdArray[Int]()
  * arr.append(42)
  * arr.append(7)
  * val n = arr.size          // 2
  * val x = arr(0)            // 42
  * }}}
  *
  * Note: `GdArray[A]` does not currently manage refcount lifetime — the internal Godot Array data
  * is never explicitly freed (bounded leak). Do not store in long-lived fields unless you control
  * the object lifecycle.
  */
final class GdArray[A] private[core] (private[core] val handle: Ptr[Byte]):

    private inline def callBM(
        fnPtr: Ptr[Byte],
        args: Ptr[Ptr[Byte]],
        ret: Ptr[Byte],
        argCount: Int
    ): Unit =
        val fn = CFuncPtr.fromPtr[CFuncPtr4[Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], CInt, Unit]](
          fnPtr
        )
        fn(handle, args, ret, argCount)

    /** Number of elements in the array. */
    def size: Int =
        if GdxApi.arraySizeFnPtr == null then return 0
        val ret = stackalloc[Long]()
        callBM(GdxApi.arraySizeFnPtr, null, ret.asInstanceOf[Ptr[Byte]], 0)
        (!ret).toInt

    def isEmpty: Boolean = size == 0

    /** Return the element at `index`. Godot will print an error if index is out of bounds. */
    def apply(index: Int)(using fv: FromVariant[A]): A =
        if GdxApi.arrayGetFnPtr == null then return fv.read(null)
        val varBuf = stackalloc[Byte](24)
        val iStore = stackalloc[Long]()
        val args   = stackalloc[Ptr[Byte]](1)
        memset(varBuf, 0, 24.toUSize)
        !iStore = index.toLong
        args(0) = iStore.asInstanceOf[Ptr[Byte]]
        callBM(GdxApi.arrayGetFnPtr, args, varBuf, 1)
        fv.read(varBuf)

    /** Set the element at `index`. Godot will print an error if index is out of bounds. */
    def update(index: Int, value: A)(using tv: ToVariant[A]): Unit =
        if GdxApi.arraySetFnPtr == null then return
        val varBuf = stackalloc[Byte](24)
        val iStore = stackalloc[Long]()
        val args   = stackalloc[Ptr[Byte]](2)
        memset(varBuf, 0, 24.toUSize)
        !iStore = index.toLong
        tv.write(varBuf, value)
        args(0) = iStore.asInstanceOf[Ptr[Byte]]
        args(1) = varBuf
        callBM(GdxApi.arraySetFnPtr, args, null, 2)

    /** Append `value` at the end of the array. */
    def append(value: A)(using tv: ToVariant[A]): Unit =
        if GdxApi.arrayPushBackFnPtr == null then return
        val varBuf = stackalloc[Byte](24)
        val args   = stackalloc[Ptr[Byte]](1)
        memset(varBuf, 0, 24.toUSize)
        tv.write(varBuf, value)
        args(0) = varBuf
        callBM(GdxApi.arrayPushBackFnPtr, args, null, 1)

    /** Remove all elements from the array. */
    def clear(): Unit =
        if GdxApi.arrayClearFnPtr == null then return
        callBM(GdxApi.arrayClearFnPtr, null, null, 0)

    def toOpt: Option[GdArray[A]] = if handle == null then None else Some(this)

end GdArray

object GdArray:
    /** Create an empty `GdArray[A]`. The handle is heap-allocated and initialized by Godot's
      * default Array constructor.
      */
    def apply[A](): GdArray[A] =
        val handle = malloc(8).asInstanceOf[Ptr[Byte]]
        memset(handle, 0, 8.toUSize)
        if GdxApi.arrayDefaultCtorPtr != null then
            val fn = CFuncPtr.fromPtr[CFuncPtr2[Ptr[Byte], Ptr[Ptr[Byte]], Unit]](
              GdxApi.arrayDefaultCtorPtr
            )
            fn(handle, null)
        new GdArray[A](handle)

    /** Wrap an existing 8-byte Array handle (e.g. received from a Variant). The handle's memory
      * must outlive this GdArray.
      */
    def fromHandle[A](handle: Ptr[Byte]): GdArray[A] = new GdArray[A](handle)

    // ── Variant marshalling ────────────────────────────────────────────────────

    given toVariantGdArray: [A] => ToVariant[GdArray[A]] =
        new ToVariant[GdArray[A]]:
            def variantType = VariantType.Array
            def write(dest: Ptr[Byte], value: GdArray[A]): Unit =
                !(dest.asInstanceOf[Ptr[Int]]) = VariantType.Array
                if value != null && value.handle != null then
                    memcpy(dest + 8, value.handle, 8.toUSize)

    given fromVariantGdArray: [A] => FromVariant[GdArray[A]] =
        new FromVariant[GdArray[A]]:
            def read(src: Ptr[Byte]): GdArray[A] =
                // The variant's data section holds the 8-byte Array handle by value.
                // Copy it to heap so the GdArray outlives the Variant.
                val handle = malloc(8).asInstanceOf[Ptr[Byte]]
                if src != null then memcpy(handle, src + 8, 8.toUSize)
                else memset(handle, 0, 8.toUSize)
                new GdArray[A](handle)

    given exportTypeGdArray: [A] => ExportType[GdArray[A]] =
        new ExportType[GdArray[A]]:
            def variantType = VariantType.Array
            def write(dest: Ptr[Byte], value: GdArray[A]): Unit =
                !(dest.asInstanceOf[Ptr[Int]]) = VariantType.Array
                if value != null && value.handle != null then
                    memcpy(dest + 8, value.handle, 8.toUSize)
            def read(src: Ptr[Byte]): GdArray[A] =
                val handle = malloc(8).asInstanceOf[Ptr[Byte]]
                if src != null then memcpy(handle, src + 8, 8.toUSize)
                else memset(handle, 0, 8.toUSize)
                new GdArray[A](handle)

    given defaultGdArray: [A] => DefaultValue[GdArray[A]] =
        new DefaultValue[GdArray[A]]:
            def default = GdArray[A]()
end GdArray
