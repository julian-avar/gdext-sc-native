package com.`julian-avar`.gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*

/** A typed wrapper around Godot's `Array` builtin type with value-type semantics.
  *
  * `GdArray[A]` behaves like `Vector2` or `Color` — the handle (8 bytes) is stored inline in the
  * Scala wrapper. Godot's internal reference counting manages the backing data. No manual
  * `destroy()` or `free()` is needed in the high-level API.
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
  * Exported arrays are extension-lifetime handles. Local arrays implicitly leak the Godot backing
  * data when they go out of scope — this matches the value-type contract. For leak-conscious hot
  * loops, use the low-level API (future) with explicit `alloc()`/`free()`.
  */
final class GdArray[A] private[core] (private[core] val handle: Ptr[Byte]):

    private inline def callBM(
        fnPtr: Ptr[Byte],
        args: Ptr[Ptr[Byte]],
        ret: Ptr[Byte],
        argCount: Int
    ): Unit =
        val fn = CFuncPtr
            .fromPtr[CFuncPtr4[Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], CInt, Unit]](fnPtr)
        fn(handle, args, ret, argCount)
    end callBM

    /** Number of elements in the array. */
    def size: Int =
        if GdxApi.arraySizeFnPtr == null then return 0
        val ret = stackalloc[Long]()
        callBM(GdxApi.arraySizeFnPtr, null, ret.asInstanceOf[Ptr[Byte]], 0)
        (!ret).toInt
    end size

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
        val result = fv.read(varBuf)
        GdxApi.destroyVariant(varBuf)
        result
    end apply

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
        GdxApi.destroyVariant(varBuf)
    end update

    /** Append `value` at the end of the array. */
    def append(value: A)(using tv: ToVariant[A]): Unit =
        if GdxApi.arrayPushBackFnPtr == null then return
        val varBuf = stackalloc[Byte](24)
        val args   = stackalloc[Ptr[Byte]](1)
        memset(varBuf, 0, 24.toUSize)
        tv.write(varBuf, value)
        args(0) = varBuf
        callBM(GdxApi.arrayPushBackFnPtr, args, null, 1)
        GdxApi.destroyVariant(varBuf)
    end append

    /** Remove all elements from the array. */
    def clear(): Unit =
        if GdxApi.arrayClearFnPtr == null then return
        callBM(GdxApi.arrayClearFnPtr, null, null, 0)

    /** Returns true if `value` is in the array. */
    def contains(value: A)(using tv: ToVariant[A]): Boolean =
        if GdxApi.arrayHasFnPtr == null then return false
        val varBuf = stackalloc[Byte](24)
        val args   = stackalloc[Ptr[Byte]](1)
        val ret    = stackalloc[Byte](1)
        memset(varBuf, 0, 24.toUSize)
        tv.write(varBuf, value)
        args(0) = varBuf
        callBM(GdxApi.arrayHasFnPtr, args, ret, 1)
        GdxApi.destroyVariant(varBuf)
        !ret != 0.toByte
    end contains

    /** Returns the index of the first occurrence of `value`, or -1 if not found. */
    def indexOf(value: A, from: Int = 0)(using tv: ToVariant[A]): Int =
        if GdxApi.arrayFindFnPtr == null then return -1
        val varBuf  = stackalloc[Byte](24)
        val fromBuf = stackalloc[Long]()
        val args    = stackalloc[Ptr[Byte]](2)
        val ret     = stackalloc[Long]()
        memset(varBuf, 0, 24.toUSize)
        tv.write(varBuf, value)
        !fromBuf = from.toLong
        args(0) = varBuf; args(1) = fromBuf.asInstanceOf[Ptr[Byte]]
        callBM(GdxApi.arrayFindFnPtr, args, ret.asInstanceOf[Ptr[Byte]], 2)
        GdxApi.destroyVariant(varBuf)
        (!ret).toInt
    end indexOf

    /** Insert `value` at `index`, shifting all later elements right. */
    def insert(index: Int, value: A)(using tv: ToVariant[A]): Unit =
        if GdxApi.arrayInsertFnPtr == null then return
        val idxBuf = stackalloc[Long]()
        val varBuf = stackalloc[Byte](24)
        val args   = stackalloc[Ptr[Byte]](2)
        memset(varBuf, 0, 24.toUSize)
        !idxBuf = index.toLong
        tv.write(varBuf, value)
        args(0) = idxBuf.asInstanceOf[Ptr[Byte]]; args(1) = varBuf
        callBM(GdxApi.arrayInsertFnPtr, args, null, 2)
        GdxApi.destroyVariant(varBuf)
    end insert

    /** Remove the element at `index`. */
    def removeAt(index: Int): Unit =
        if GdxApi.arrayRemoveAtFnPtr == null then return
        val idxBuf = stackalloc[Long]()
        val args   = stackalloc[Ptr[Byte]](1)
        !idxBuf = index.toLong
        args(0) = idxBuf.asInstanceOf[Ptr[Byte]]
        callBM(GdxApi.arrayRemoveAtFnPtr, args, null, 1)
    end removeAt

    /** Remove the first occurrence of `value` from the array. */
    def erase(value: A)(using tv: ToVariant[A]): Unit =
        if GdxApi.arrayEraseFnPtr == null then return
        val varBuf = stackalloc[Byte](24)
        val args   = stackalloc[Ptr[Byte]](1)
        memset(varBuf, 0, 24.toUSize)
        tv.write(varBuf, value)
        args(0) = varBuf
        callBM(GdxApi.arrayEraseFnPtr, args, null, 1)
        GdxApi.destroyVariant(varBuf)
    end erase

    /** Sort the array in ascending order (uses Godot's built-in comparison). */
    def sort(): Unit =
        if GdxApi.arraySortFnPtr == null then return
        callBM(GdxApi.arraySortFnPtr, null, null, 0)

    /** Iterate over all elements. */
    def foreach(fn: A => Unit)(using fv: FromVariant[A]): Unit =
        val n = size
        var i = 0
        while i < n do
            fn(apply(i))
            i += 1
    end foreach

    /** Collect all elements into a Scala List. */
    def toList(using fv: FromVariant[A]): List[A] =
        val buf = List.newBuilder[A]
        foreach(buf += _)
        buf.result()
    end toList

end GdArray

object GdArray:
    /** Create an empty `GdArray[A]`. The handle is heap-allocated and initialized by Godot's
      * default Array constructor.
      */
    def apply[A](): GdArray[A] =
        val handle = malloc(8).asInstanceOf[Ptr[Byte]]
        memset(handle, 0, 8.toUSize)
        if GdxApi.arrayDefaultCtorPtr != null then
            val fn = CFuncPtr
                .fromPtr[CFuncPtr2[Ptr[Byte], Ptr[Ptr[Byte]], Unit]](GdxApi.arrayDefaultCtorPtr)
            fn(handle, null)
        end if
        new GdArray[A](handle)
    end apply

    /** Alias for `apply[A]()` — creates an empty `GdArray[A]`. */
    def empty[A]: GdArray[A] = apply[A]()

    /** Wrap an existing 8-byte Array handle. Internal — used by GdDict.keys()/values(). The
      * handle's memory must outlive this GdArray.
      */
    private[core] def fromHandle[A](handle: Ptr[Byte]): GdArray[A] = new GdArray[A](handle)

    // ── Variant marshalling (value-type semantics — no manual destroy) ──────────

    given toVariantGdArray: [A] => ToVariant[GdArray[A]] = new ToVariant[GdArray[A]]:
        def variantType                                     = VariantType.Array
        def write(dest: Ptr[Byte], value: GdArray[A]): Unit =
            !(dest.asInstanceOf[Ptr[Int]]) = VariantType.Array
            if value != null && value.handle != null then
                GdxApi.copyArrayHandle(dest + 8, value.handle)
            else memset(dest + 8, 0, 8.toUSize)
        end write

    given fromVariantGdArray: [A] => FromVariant[GdArray[A]] = new FromVariant[GdArray[A]]:
        def read(src: Ptr[Byte]): GdArray[A] =
            val handle = malloc(8).asInstanceOf[Ptr[Byte]]
            if src != null then
                // Use copy constructor for proper refcounting — the Variant's Array handle at
                // src+8 is copied with incremented refcount. The Variant can then be safely
                // destroyed via GdxApi.destroyVariant(src) by the caller.
                memset(handle, 0, 8.toUSize)
                GdxApi.copyArrayHandle(handle, src + 8)
            else memset(handle, 0, 8.toUSize)
            end if
            new GdArray[A](handle)
        end read

    given exportTypeGdArray: [A] => (ExportType[A]) => ExportType[GdArray[A]] =
        val et = summon[ExportType[A]]
        new ExportType[GdArray[A]]:
            def variantType         = VariantType.Array
            override def hint       = PropertyHint.ArrayType
            override def hintString =
                if et.variantType == VariantType.Object && et.className.nonEmpty then
                    s"${et.variantType}/${PropertyHint.ResourceType}/${et.className}:${et
                            .className}"
                else et.variantType.toString
            override def className                              = ""
            override def usage                                  = PropertyUsage.Default
            def write(dest: Ptr[Byte], value: GdArray[A]): Unit =
                !(dest.asInstanceOf[Ptr[Int]]) = VariantType.Array
                if value != null && value.handle != null then
                    GdxApi.copyArrayHandle(dest + 8, value.handle)
                else memset(dest + 8, 0, 8.toUSize)
            end write
            def read(src: Ptr[Byte]): GdArray[A] =
                val handle = malloc(8).asInstanceOf[Ptr[Byte]]
                if src != null then
                    memset(handle, 0, 8.toUSize)
                    GdxApi.copyArrayHandle(handle, src + 8)
                else memset(handle, 0, 8.toUSize)
                end if
                new GdArray[A](handle)
            end read
        end new
    end exportTypeGdArray

    given defaultGdArray: [A] => DefaultValue[GdArray[A]] = new DefaultValue[GdArray[A]]:
        def default = GdArray[A]()
end GdArray
