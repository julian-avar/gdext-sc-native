package gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import gdext.generated.RID

/** Writes a Scala value into a 24-byte Godot Variant buffer and declares the GDExtension variant
  * type tag.
  */
trait ToVariant[A]:
    def variantType: Int
    def write(dest: Ptr[Byte], value: A): Unit

/** Reads a Scala value from a 24-byte Godot Variant buffer. */
trait FromVariant[A]:
    def read(src: Ptr[Byte]): A

object ToVariant:
    given ToVariant[Boolean]:
        def variantType                            = VariantType.Bool
        def write(dest: Ptr[Byte], value: Boolean) = Variant.writeBool(dest, value)

    given ToVariant[Int]:
        def variantType                        = VariantType.Int
        def write(dest: Ptr[Byte], value: Int) = Variant.writeInt(dest, value.toLong)

    given ToVariant[Long]:
        def variantType                         = VariantType.Int
        def write(dest: Ptr[Byte], value: Long) = Variant.writeInt(dest, value)

    given ToVariant[Float]:
        def variantType                          = VariantType.Float
        def write(dest: Ptr[Byte], value: Float) = Variant.writeFloat(dest, value.toDouble)

    given ToVariant[Double]:
        def variantType                           = VariantType.Float
        def write(dest: Ptr[Byte], value: Double) = Variant.writeFloat(dest, value)

    given ToVariant[String]:
        def variantType                           = VariantType.String
        def write(dest: Ptr[Byte], value: String) = Variant.writeString(dest, value)

    given toVariantGd: [T <: GodotObject] => (GodotClass[T]) => ToVariant[Gd[T]] =
        new ToVariant[Gd[T]]:
            def variantType                                = VariantType.Object
            def write(dest: Ptr[Byte], value: Gd[T]): Unit =
                !(dest.asInstanceOf[Ptr[Int]]) = VariantType.Object
                !(dest + 8).asInstanceOf[Ptr[Ptr[Byte]]] =
                    if value.isNull then null else value.objectPtr
            end write

    /** ToVariant for any GodotObject subclass T directly (not wrapped in Gd[T]).
      *
      * Enables `@func def getTarget(): Node2D` — method returns T, not Gd[T]. Uses `GodotClass[T]`
      * for the class name (implicit resolution) but only needs `.ptr`.
      */
    given toVariantObj: [T <: GodotObject] => (GodotClass[T]) => ToVariant[T] = new ToVariant[T]:
        def variantType                            = VariantType.Object
        def write(dest: Ptr[Byte], value: T): Unit =
            !(dest.asInstanceOf[Ptr[Int]]) = VariantType.Object
            !(dest + 8).asInstanceOf[Ptr[Ptr[Byte]]] = if value == null then null else value.ptr
    given toVariantRid: ToVariant[RID] = new ToVariant[RID]:
        def variantType                              = VariantType.RID
        def write(dest: Ptr[Byte], value: RID): Unit =
            !(dest.asInstanceOf[Ptr[Int]]) = VariantType.RID
            !(dest + 8).asInstanceOf[Ptr[Long]] =
                if value != null && value.ptr != null then !(value.ptr.asInstanceOf[Ptr[Long]])
                else 0L
        end write

end ToVariant

object FromVariant:
    given FromVariant[Boolean]:
        def read(src: Ptr[Byte]): Boolean = Variant.readBool(src)

    given FromVariant[Int]:
        def read(src: Ptr[Byte]): Int = Variant.readInt(src).toInt

    given FromVariant[Long]:
        def read(src: Ptr[Byte]): Long = Variant.readInt(src)

    given FromVariant[Float]:
        def read(src: Ptr[Byte]): Float = Variant.readFloat(src).toFloat

    given FromVariant[Double]:
        def read(src: Ptr[Byte]): Double = Variant.readFloat(src)

    given FromVariant[String]:
        def read(src: Ptr[Byte]): String = Variant.readString(src)

    given fromVariantGd: [T <: GodotObject] => (GodotClass[T]) => FromVariant[Gd[T]] =
        val gc = summon[GodotClass[T]]
        new FromVariant[Gd[T]]:
            def read(src: Ptr[Byte]): Gd[T] = new Gd[T](!(src + 8).asInstanceOf[Ptr[Ptr[Byte]]], gc)
    end fromVariantGd

    /** FromVariant for any GodotObject subclass T directly (not wrapped in Gd[T]).
      *
      * Enables `@func def onBodyEntered(body: PhysicsBody2D)` — param is T, not Gd[T]. With
      * instance identity in place, `gc.wrap` returns the live Scala instance for user-defined
      * classes; for engine classes it creates a fresh wrapper (correct).
      */
    given fromVariantObj: [T <: GodotObject] => (GodotClass[T]) => FromVariant[T] =
        val gc = summon[GodotClass[T]]
        new FromVariant[T]:
            def read(src: Ptr[Byte]): T =
                val ptr = !(src + 8).asInstanceOf[Ptr[Ptr[Byte]]]
                if ptr == null then null.asInstanceOf[T] else gc.wrap(ptr)
        end new
    end fromVariantObj

    given fromVariantRid: FromVariant[RID] = new FromVariant[RID]:
        def read(src: Ptr[Byte]): RID =
            import scala.scalanative.libc.stdlib.malloc
            val buf = malloc(sizeof[Long])
            !(buf.asInstanceOf[Ptr[Long]]) = !(src + 8).asInstanceOf[Ptr[Long]]
            new RID(buf)
        end read

end FromVariant
