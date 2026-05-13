package gdext.core

import scala.scalanative.unsafe.*

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
    setter: (GodotObject, Ptr[Byte]) => Unit
)

object VariantType:
    val Nil    = 0
    val Bool   = 1
    val Int    = 2
    val Float  = 3
    val String = 4
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
end Variant
