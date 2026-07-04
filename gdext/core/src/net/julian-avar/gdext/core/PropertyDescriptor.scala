package net.`julian-avar`.gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

/** Describes an exported property. Used manually in the entry point until macros are available.
  *
  * Example (what a future @export macro would generate):
  * {{{
  * PropertyDescriptor(
  *   name = "speed",
  *   variantType = VariantType.Int,
  *   getter = (obj, ret) => Variant.writeInt(ret, obj.asInstanceOf[PlayerSc].speed.toLong),
  *   setter = (obj, v)   => obj.asInstanceOf[PlayerSc].speed = Variant.readInt(v).toInt
  * )
  * }}}
  */
case class PropertyDescriptor(
    name: String,
    variantType: Int,
    getter: (GodotObject, Ptr[Byte]) => Unit,
    setter: (GodotObject, Ptr[Byte]) => Unit,
    hint: Int = PropertyHint.None,
    hintString: String = "",
    propClassName: String = "",
    usage: Int = PropertyUsage.Default
)

object PropertyDescriptor:
    /** Build a descriptor using typeclass-based marshalling. */
    def of[A](name: String, get: GodotObject => A, set: (GodotObject, A) => Unit)(using
        tv: ToVariant[A],
        fv: FromVariant[A]
    ): PropertyDescriptor = PropertyDescriptor(
      name = name,
      variantType = tv.variantType,
      getter = (obj, ret) => tv.write(ret, get(obj)),
      setter = (obj, v) => set(obj, fv.read(v))
    )
end PropertyDescriptor

object PropertyHint:
    val None          = 0
    val Range         = 1
    val Enum          = 2
    val File          = 13
    val Dir           = 14
    val GlobalFile    = 15
    val GlobalDir     = 16
    val ResourceType  = 17
    val MultilineText = 18
    val ColorNoAlpha  = 21
    val ArrayType     = 31 // Godot 4.x: PROPERTY_HINT_ARRAY_TYPE = 31
    val NodeType      = 34 // Godot 4.x: PROPERTY_HINT_NODE_TYPE = 34
end PropertyHint

object PropertyUsage:
    val None        = 0
    val Storage     = 2
    val Editor      = 4
    val Default     = Storage | Editor // = 6
    val Group       = 64               // PROPERTY_USAGE_GROUP
    val Category    = 128              // PROPERTY_USAGE_CATEGORY
    val Subgroup    = 256              // PROPERTY_USAGE_SUBGROUP
    val ClassIsEnum = 65536            // PROPERTY_USAGE_CLASS_IS_ENUM
end PropertyUsage

object VariantType:
    val Nil                = 0
    val Bool               = 1
    val Int                = 2
    val Float              = 3
    val String             = 4
    val Vector2            = 5
    val Vector2i           = 6
    val Rect2              = 7
    val Rect2i             = 8
    val Vector3            = 9
    val Vector3i           = 10
    val Transform2D        = 11
    val Vector4            = 12
    val Vector4i           = 13
    val Plane              = 14
    val Quaternion         = 15
    val AABB               = 16
    val Basis              = 17
    val Transform3D        = 18
    val Projection         = 19
    val Color              = 20
    val StringName         = 21
    val NodePath           = 22
    val RID                = 23
    val Object             = 24
    val Callable           = 25
    val Signal             = 26
    val Dictionary         = 27
    val Array              = 28
    val PackedByteArray    = 29
    val PackedInt32Array   = 30
    val PackedInt64Array   = 31
    val PackedFloat32Array = 32
    val PackedFloat64Array = 33
    val PackedStringArray  = 34
    val PackedVector2Array = 35
    val PackedVector3Array = 36
    val PackedColorArray   = 37
    val PackedVector4Array = 38
end VariantType

/** Helpers for reading and writing Godot Variant values (24-byte structs).
  *
  * Layout: bytes 0-3 = type tag (uint32), bytes 4-7 = padding, bytes 8-15 = payload.
  */
object Variant:
    def readInt(p: Ptr[Byte]): Long           = !(p.asInstanceOf[Ptr[Long]] + 1)
    def writeInt(p: Ptr[Byte], v: Long): Unit =
        !(p.asInstanceOf[Ptr[Int]]) = VariantType.Int
        !(p.asInstanceOf[Ptr[Long]] + 1) = v

    def readFloat(p: Ptr[Byte]): Double           = !(p.asInstanceOf[Ptr[Double]] + 1)
    def writeFloat(p: Ptr[Byte], v: Double): Unit =
        !(p.asInstanceOf[Ptr[Int]]) = VariantType.Float
        !(p.asInstanceOf[Ptr[Double]] + 1) = v

    def readBool(p: Ptr[Byte]): Boolean           = !(p + 8).asInstanceOf[Ptr[Byte]] != 0
    def writeBool(p: Ptr[Byte], v: Boolean): Unit =
        !(p.asInstanceOf[Ptr[Int]]) = VariantType.Bool
        !(p + 8) = (if v then 1 else 0).toByte

    def writeString(p: Ptr[Byte], v: String): Unit = Zone {
        import scala.scalanative.libc.string.memset
        val strBuf = stackalloc[Byte](8)
        memset(strBuf, 0, 8.toUSize)
        GdxApi.initGodotString(strBuf, toCString(v))
        GdxApi.buildStringVariant(p, strBuf)
        GdxApi.destroyGodotString(strBuf)
    }

    def readString(p: Ptr[Byte]): String =
        import scala.scalanative.libc.string.memset
        val strBuf = stackalloc[Byte](8)
        memset(strBuf, 0, 8.toUSize)
        GdxApi.extractStringFromVariant(p, strBuf)
        val s = GdxApi.godotStringToScala(strBuf)
        GdxApi.destroyGodotString(strBuf)
        s
    end readString

    /** Write a Godot value-type struct into a 24-byte Variant buffer.
      *
      * Copies `sizeof[T]` bytes from `value` into the Variant's data section (offset 8) and sets
      * the type tag. T must be a concrete CStruct type with a known Tag.
      */
    def writeBuiltin[T](typeCode: Int, dest: Ptr[Byte], value: Ptr[T])(using Tag[T]): Unit =
        import scala.scalanative.libc.string.memcpy
        !(dest.asInstanceOf[Ptr[Int]]) = typeCode
        memcpy(dest + 8, value.asInstanceOf[Ptr[Byte]], sizeof[T])
    end writeBuiltin

    /** Read a Godot value-type struct out of a 24-byte Variant buffer.
      *
      * Heap-allocates a new T, copies `sizeof[T]` bytes from the Variant's data section. The caller
      * owns the returned pointer.
      */
    def readBuiltin[T](src: Ptr[Byte])(using Tag[T]): Ptr[T] =
        import scala.scalanative.libc.stdlib.malloc
        import scala.scalanative.libc.string.memcpy
        val dest = malloc(sizeof[T]).asInstanceOf[Ptr[T]]
        memcpy(dest.asInstanceOf[Ptr[Byte]], src + 8, sizeof[T])
        dest
    end readBuiltin
end Variant
