package gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*

/** A typed wrapper around Godot's `Dictionary` builtin type.
  *
  * `GdDict[K, V]` is a reference-counted heap-allocated handle (8 bytes). Keys and values are
  * marshalled through Variant so any type with `ToVariant`/`FromVariant` instances can be used.
  *
  * {{{
  * val d = GdDict[String, Int]()
  * d("health") = 100
  * d("speed") = 400
  * val hp = d("health")  // 100
  * }}}
  *
  * Note: Does not currently manage refcount lifetime — the internal Godot Dictionary data is never
  * explicitly freed (bounded leak).
  */
final class GdDict[K, V] private[core] (private[core] val handle: Ptr[Byte]):

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

    def size: Int =
        if GdxApi.dictSizeFnPtr == null then return 0
        val ret = stackalloc[Long]()
        callBM(GdxApi.dictSizeFnPtr, null, ret.asInstanceOf[Ptr[Byte]], 0)
        (!ret).toInt

    def isEmpty: Boolean = size == 0

    def has(key: K)(using tv: ToVariant[K]): Boolean =
        if GdxApi.dictHasFnPtr == null then return false
        val kBuf = stackalloc[Byte](24)
        val args = stackalloc[Ptr[Byte]](1)
        val ret  = stackalloc[Byte](1)
        memset(kBuf, 0, 24.toUSize)
        tv.write(kBuf, key)
        args(0) = kBuf
        callBM(GdxApi.dictHasFnPtr, args, ret, 1)
        !ret != 0.toByte

    /** Return the value for `key`. If the key is absent, returns the `defaultValue`. */
    def get(key: K, defaultValue: V)(using tv: ToVariant[K], tvv: ToVariant[V], fv: FromVariant[V]): V =
        if GdxApi.dictGetFnPtr == null then return defaultValue
        val kBuf   = stackalloc[Byte](24)
        val defBuf = stackalloc[Byte](24)
        val retBuf = stackalloc[Byte](24)
        val args   = stackalloc[Ptr[Byte]](2)
        memset(kBuf, 0, 24.toUSize)
        memset(defBuf, 0, 24.toUSize)
        tv.write(kBuf, key)
        tvv.write(defBuf, defaultValue)
        args(0) = kBuf
        args(1) = defBuf
        callBM(GdxApi.dictGetFnPtr, args, retBuf, 2)
        fv.read(retBuf)

    /** Scala update syntax: `dict(key) = value` */
    def apply(key: K)(using tv: ToVariant[K], tvv: ToVariant[V], fv: FromVariant[V], dvV: DefaultValue[V]): V =
        get(key, dvV.default)

    def update(key: K, value: V)(using tkv: ToVariant[K], tvv: ToVariant[V]): Unit =
        if GdxApi.dictSetFnPtr == null then return
        val kBuf = stackalloc[Byte](24)
        val vBuf = stackalloc[Byte](24)
        val args = stackalloc[Ptr[Byte]](2)
        memset(kBuf, 0, 24.toUSize)
        memset(vBuf, 0, 24.toUSize)
        tkv.write(kBuf, key)
        tvv.write(vBuf, value)
        args(0) = kBuf
        args(1) = vBuf
        callBM(GdxApi.dictSetFnPtr, args, null, 2)

    def erase(key: K)(using tv: ToVariant[K]): Unit =
        if GdxApi.dictEraseFnPtr == null then return
        val kBuf = stackalloc[Byte](24)
        val args = stackalloc[Ptr[Byte]](1)
        memset(kBuf, 0, 24.toUSize)
        tv.write(kBuf, key)
        args(0) = kBuf
        callBM(GdxApi.dictEraseFnPtr, args, null, 1)

    def clear(): Unit =
        if GdxApi.dictClearFnPtr == null then return
        callBM(GdxApi.dictClearFnPtr, null, null, 0)

end GdDict

object GdDict:
    def apply[K, V](): GdDict[K, V] =
        val handle = malloc(8).asInstanceOf[Ptr[Byte]]
        memset(handle, 0, 8.toUSize)
        if GdxApi.dictDefaultCtorPtr != null then
            val fn = CFuncPtr.fromPtr[CFuncPtr2[Ptr[Byte], Ptr[Ptr[Byte]], Unit]](
              GdxApi.dictDefaultCtorPtr
            )
            fn(handle, null)
        new GdDict[K, V](handle)

    def fromHandle[K, V](handle: Ptr[Byte]): GdDict[K, V] = new GdDict[K, V](handle)

    given toVariantGdDict: [K, V] => ToVariant[GdDict[K, V]] =
        new ToVariant[GdDict[K, V]]:
            def variantType = VariantType.Dictionary
            def write(dest: Ptr[Byte], value: GdDict[K, V]): Unit =
                !(dest.asInstanceOf[Ptr[Int]]) = VariantType.Dictionary
                if value != null && value.handle != null then
                    memcpy(dest + 8, value.handle, 8.toUSize)

    given fromVariantGdDict: [K, V] => FromVariant[GdDict[K, V]] =
        new FromVariant[GdDict[K, V]]:
            def read(src: Ptr[Byte]): GdDict[K, V] =
                val handle = malloc(8).asInstanceOf[Ptr[Byte]]
                if src != null then memcpy(handle, src + 8, 8.toUSize)
                else memset(handle, 0, 8.toUSize)
                new GdDict[K, V](handle)

    given exportTypeGdDict: [K, V] => ExportType[GdDict[K, V]] =
        new ExportType[GdDict[K, V]]:
            def variantType = VariantType.Dictionary
            def write(dest: Ptr[Byte], value: GdDict[K, V]): Unit =
                !(dest.asInstanceOf[Ptr[Int]]) = VariantType.Dictionary
                if value != null && value.handle != null then
                    memcpy(dest + 8, value.handle, 8.toUSize)
            def read(src: Ptr[Byte]): GdDict[K, V] =
                val handle = malloc(8).asInstanceOf[Ptr[Byte]]
                if src != null then memcpy(handle, src + 8, 8.toUSize)
                else memset(handle, 0, 8.toUSize)
                new GdDict[K, V](handle)

    given defaultGdDict: [K, V] => DefaultValue[GdDict[K, V]] =
        new DefaultValue[GdDict[K, V]]:
            def default = GdDict[K, V]()
end GdDict
