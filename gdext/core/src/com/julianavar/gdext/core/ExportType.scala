package com.julianavar.gdext.core

import scala.scalanative.unsafe.*

/** Type metadata needed to export a Scala field to the Godot inspector.
  *
  * Bundles variant type, inspector hint, and Variant marshalling. The macro summons this for every
  * `@gdexport` field. `@func` arg/return types still use `ToVariant`/`FromVariant` instead.
  *
  * Givens for Godot builtin math types (Vector2, Color, etc.) are generated directly into
  * `gdext.generated` alongside their `ToVariant`/`FromVariant` givens, and are imported with
  * `gdext.generated.*`.
  */
trait ExportType[A]:
    def variantType: Int
    def hint: Int          = PropertyHint.None
    def hintString: String = ""
    def className: String  = ""
    def usage: Int         = PropertyUsage.Default
    def write(dest: Ptr[Byte], value: A): Unit
    def read(src: Ptr[Byte]): A
end ExportType

object ExportType:
    /** Export a direct engine class reference (e.g. `@gdexport var target: Node2D`).
      *
      * Shown in the Godot inspector as a NodeType property slot filtered to `className`. The `wrap`
      * call on read creates a fresh Scala wrapper around the engine object — state lives in Godot
      * so this is safe for engine classes. User-defined class exports work but return a fresh
      * (stateless) wrapper until identity preservation lands in a later phase.
      */
    given exportTypeEngineObj: [T <: GodotObject] => (GodotClass[T]) => ExportType[T] =
        val gc = summon[GodotClass[T]]
        new ExportType[T]:
            override def hint                          = PropertyHint.NodeType
            override def className                     = gc.className
            def variantType                            = VariantType.Object
            def write(dest: Ptr[Byte], value: T): Unit =
                !(dest.asInstanceOf[Ptr[Int]]) = VariantType.Object
                !(dest + 8).asInstanceOf[Ptr[Ptr[Byte]]] = if value == null then null else value.ptr
            def read(src: Ptr[Byte]): T =
                val ptr = !(src + 8).asInstanceOf[Ptr[Ptr[Byte]]]
                if ptr == null then null.asInstanceOf[T] else gc.wrap(ptr)
        end new
    end exportTypeEngineObj

    /** Export a typed Gd[T] pointer (e.g. `@gdexport var node: Gd[Node2D]`). */
    given exportTypeGd: [T <: GodotObject] => (GodotClass[T]) => ExportType[Gd[T]] =
        val gc = summon[GodotClass[T]]
        new ExportType[Gd[T]]:
            override def hint                              = PropertyHint.NodeType
            override def className                         = gc.className
            def variantType                                = VariantType.Object
            def write(dest: Ptr[Byte], value: Gd[T]): Unit =
                !(dest.asInstanceOf[Ptr[Int]]) = VariantType.Object
                !(dest + 8).asInstanceOf[Ptr[Ptr[Byte]]] =
                    if value.isNull then null else value.objectPtr
            end write
            def read(src: Ptr[Byte]): Gd[T] = new Gd[T](!(src + 8).asInstanceOf[Ptr[Ptr[Byte]]], gc)
        end new
    end exportTypeGd

    given ExportType[Boolean]:
        def variantType                            = VariantType.Bool
        def write(dest: Ptr[Byte], value: Boolean) = Variant.writeBool(dest, value)
        def read(src: Ptr[Byte]): Boolean          = Variant.readBool(src)
    end given

    given ExportType[Int]:
        def variantType                        = VariantType.Int
        def write(dest: Ptr[Byte], value: Int) = Variant.writeInt(dest, value.toLong)
        def read(src: Ptr[Byte]): Int          = Variant.readInt(src).toInt
    end given

    given ExportType[Long]:
        def variantType                         = VariantType.Int
        def write(dest: Ptr[Byte], value: Long) = Variant.writeInt(dest, value)
        def read(src: Ptr[Byte]): Long          = Variant.readInt(src)
    end given

    given ExportType[Float]:
        def variantType                          = VariantType.Float
        def write(dest: Ptr[Byte], value: Float) = Variant.writeFloat(dest, value.toDouble)
        def read(src: Ptr[Byte]): Float          = Variant.readFloat(src).toFloat
    end given

    given ExportType[Double]:
        def variantType                           = VariantType.Float
        def write(dest: Ptr[Byte], value: Double) = Variant.writeFloat(dest, value)
        def read(src: Ptr[Byte]): Double          = Variant.readFloat(src)
    end given

    given ExportType[String]:
        def variantType                           = VariantType.String
        def write(dest: Ptr[Byte], value: String) = Variant.writeString(dest, value)
        def read(src: Ptr[Byte]): String          = Variant.readString(src)
    end given
end ExportType
