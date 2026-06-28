package gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.free
import gdext.generated.*

// ── Shared size helper ──────────────────────────────────────────────────────
// All packed array types share the same `size()` hash. typeId is the Godot
// variant type constant (29–38). Returns 0 if the pointer is null or uninitialized.

private def packedArraySize(arr: Ptr[Byte], typeId: Int): Int =
    val fn = GdxApi.packedSizeFns(typeId)
    if arr == null || fn == null then return 0
    val ret = stackalloc[Long]()
    val callFn = CFuncPtr.fromPtr[CFuncPtr4[Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], CInt, Unit]](fn)
    callFn(arr, null, ret.asInstanceOf[Ptr[Byte]], 0)
    (!ret).toInt
end packedArraySize

// Variant type IDs for packed arrays (from GDExtension enum).
private inline val VT_PACKED_BYTE    = 29
private inline val VT_PACKED_INT32   = 30
private inline val VT_PACKED_INT64   = 31
private inline val VT_PACKED_FLOAT32 = 32
private inline val VT_PACKED_FLOAT64 = 33
private inline val VT_PACKED_STRING  = 34
private inline val VT_PACKED_VEC2    = 35
private inline val VT_PACKED_VEC3    = 36
private inline val VT_PACKED_COLOR   = 37
private inline val VT_PACKED_VEC4    = 38

// Calls the packed array's destructor. Call once after you're done using the array
// returned by an engine method (the engine gave us ownership of a fresh copy).
// Calls the Godot destructor (frees internal element storage) then frees the 16-byte header.
// Only call on arrays returned by engine methods (malloc'd by the generated wrappers).
private def destroyPackedArray(arr: Ptr[Byte], typeId: Int): Unit =
    val fn = GdxApi.packedDtors(typeId)
    if arr != null && fn != null then
        val dtor = CFuncPtr.fromPtr[CFuncPtr1[Ptr[Byte], Unit]](fn)
        dtor(arr)
    if arr != null then free(arr)
end destroyPackedArray

// ── PackedStringArray ────────────────────────────────────────────────────────

extension (a: PackedStringArray)
    def size: Int = packedArraySize(a.ptr, VT_PACKED_STRING)

    def apply(i: Int): String =
        val fn = GdxApi.packedStringArrayIndexFn
        if a.ptr == null || fn == null then return ""
        val strPtr = fn(a.ptr, i.toLong)
        if strPtr == null then "" else GdxApi.godotStringToScala(strPtr)
    end apply

    /** Convert to a Scala Seq. Destroys the Godot array handle afterward (caller no longer owns it).
      * Typical usage: `node.getGroups().toSeq`
      */
    def toSeq: Seq[String] =
        val n   = a.size
        val buf = new Array[String](n)
        var i   = 0
        while i < n do buf(i) = a.apply(i); i += 1
        destroyPackedArray(a.ptr, VT_PACKED_STRING)
        buf.toSeq
    end toSeq
end extension

// ── PackedByteArray ──────────────────────────────────────────────────────────

extension (a: PackedByteArray)
    def size: Int = packedArraySize(a.ptr, VT_PACKED_BYTE)

    def apply(i: Int): Byte =
        val fn = GdxApi.packedByteArrayIndexFn
        if a.ptr == null || fn == null then return 0.toByte
        !fn(a.ptr, i.toLong)
    end apply

    def toArray: Array[Byte] =
        val n   = a.size
        val buf = new Array[Byte](n)
        var i   = 0
        while i < n do buf(i) = a.apply(i); i += 1
        destroyPackedArray(a.ptr, VT_PACKED_BYTE)
        buf
    end toArray
end extension

// ── PackedInt32Array ─────────────────────────────────────────────────────────

extension (a: PackedInt32Array)
    def size: Int = packedArraySize(a.ptr, VT_PACKED_INT32)

    def apply(i: Int): Int =
        val fn = GdxApi.packedInt32ArrayIndexFn
        if a.ptr == null || fn == null then return 0
        !fn(a.ptr, i.toLong).asInstanceOf[Ptr[Int]]
    end apply

    def toArray: Array[Int] =
        val n   = a.size
        val buf = new Array[Int](n)
        var i   = 0
        while i < n do buf(i) = a.apply(i); i += 1
        destroyPackedArray(a.ptr, VT_PACKED_INT32)
        buf
    end toArray
end extension

// ── PackedInt64Array ─────────────────────────────────────────────────────────

extension (a: PackedInt64Array)
    def size: Int = packedArraySize(a.ptr, VT_PACKED_INT64)

    def apply(i: Int): Long =
        val fn = GdxApi.packedInt64ArrayIndexFn
        if a.ptr == null || fn == null then return 0L
        !fn(a.ptr, i.toLong).asInstanceOf[Ptr[Long]]
    end apply

    def toArray: Array[Long] =
        val n   = a.size
        val buf = new Array[Long](n)
        var i   = 0
        while i < n do buf(i) = a.apply(i); i += 1
        destroyPackedArray(a.ptr, VT_PACKED_INT64)
        buf
    end toArray
end extension

// ── PackedFloat32Array ───────────────────────────────────────────────────────

extension (a: PackedFloat32Array)
    def size: Int = packedArraySize(a.ptr, VT_PACKED_FLOAT32)

    def apply(i: Int): Float =
        val fn = GdxApi.packedFloat32ArrayIndexFn
        if a.ptr == null || fn == null then return 0f
        !fn(a.ptr, i.toLong).asInstanceOf[Ptr[Float]]
    end apply

    def toArray: Array[Float] =
        val n   = a.size
        val buf = new Array[Float](n)
        var i   = 0
        while i < n do buf(i) = a.apply(i); i += 1
        destroyPackedArray(a.ptr, VT_PACKED_FLOAT32)
        buf
    end toArray
end extension

// ── PackedFloat64Array ───────────────────────────────────────────────────────

extension (a: PackedFloat64Array)
    def size: Int = packedArraySize(a.ptr, VT_PACKED_FLOAT64)

    def apply(i: Int): Double =
        val fn = GdxApi.packedFloat64ArrayIndexFn
        if a.ptr == null || fn == null then return 0.0
        !fn(a.ptr, i.toLong).asInstanceOf[Ptr[Double]]
    end apply

    def toArray: Array[Double] =
        val n   = a.size
        val buf = new Array[Double](n)
        var i   = 0
        while i < n do buf(i) = a.apply(i); i += 1
        destroyPackedArray(a.ptr, VT_PACKED_FLOAT64)
        buf
    end toArray
end extension

// ── PackedVector2/3/4/Color — size only in core; apply/toArray in gdext.generated ─

extension (a: PackedVector2Array) def size: Int = packedArraySize(a.ptr, VT_PACKED_VEC2)
extension (a: PackedVector3Array) def size: Int = packedArraySize(a.ptr, VT_PACKED_VEC3)
extension (a: PackedVector4Array) def size: Int = packedArraySize(a.ptr, VT_PACKED_VEC4)
extension (a: PackedColorArray)   def size: Int = packedArraySize(a.ptr, VT_PACKED_COLOR)
