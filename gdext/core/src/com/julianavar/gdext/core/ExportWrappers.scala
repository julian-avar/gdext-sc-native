package com.julianavar.gdext.core

import scala.scalanative.unsafe.*

/** A typed resource reference for `@gdexport` fields.
  *
  * Shows a Resource picker in the inspector filtered to the concrete type `T`.
  *
  * {{{
  * @gdexport var material: Tres[Material] = Tres.empty
  * @gdexport var curve: Tres[Curve] = Tres.empty
  * }}}
  */
opaque type Tres[T <: GodotObject] = Gd[T]

object Tres:
    def empty[T <: GodotObject](using GodotClass[T]): Tres[T] = Gd.nullOf[T]

    /** Preferred alias for `empty` — reads more naturally with GDScript conventions. */
    def none[T <: GodotObject](using GodotClass[T]): Tres[T] = Gd.nullOf[T]

    extension [T <: GodotObject](t: Tres[T]) def gd: Gd[T] = t

    given defaultTres: [T <: GodotObject] => (GodotClass[T]) => DefaultValue[Tres[T]] =
        new DefaultValue[Tres[T]]:
            def default = Gd.nullOf[T]

    given exportTypeTres: [T <: GodotObject] => (gc: GodotClass[T]) => ExportType[Tres[T]] =
        new ExportType[Tres[T]]:
            override def hint                                = PropertyHint.ResourceType
            override def className                           = gc.className
            def variantType                                  = VariantType.Object
            def write(dest: Ptr[Byte], value: Tres[T]): Unit =
                !(dest.asInstanceOf[Ptr[Int]]) = VariantType.Object
                !(dest + 8).asInstanceOf[Ptr[Ptr[Byte]]] =
                    if value.isNull then null else value.objectPtr
            end write
            def read(src: Ptr[Byte]): Tres[T] =
                new Gd[T](!(src + 8).asInstanceOf[Ptr[Ptr[Byte]]], gc)
end Tres

/** A typed scene reference for `@gdexport` fields.
  *
  * Shows a scene picker in the inspector. `T` is the expected root node type of the scene. Use
  * `Tscn[Node]` for any scene; use a concrete type like `Tscn[Player]` to signal the intended root
  * type (Godot does not enforce this at edit time).
  *
  * {{{
  * @gdexport var bulletScene: Tscn[RigidBody2D] = Tscn.empty
  * }}}
  */
opaque type Tscn[T <: GodotObject] = Gd[T]

object Tscn:
    def empty[T <: GodotObject](using GodotClass[T]): Tscn[T] = Gd.nullOf[T]

    /** Preferred alias for `empty` — reads more naturally with GDScript conventions. */
    def none[T <: GodotObject](using GodotClass[T]): Tscn[T] = Gd.nullOf[T]

    extension [T <: GodotObject](t: Tscn[T]) def gd: Gd[T] = t

    given defaultTscn: [T <: GodotObject] => (GodotClass[T]) => DefaultValue[Tscn[T]] =
        new DefaultValue[Tscn[T]]:
            def default = Gd.nullOf[T]

    given exportTypeTscn: [T <: GodotObject] => (gc: GodotClass[T]) => ExportType[Tscn[T]] =
        new ExportType[Tscn[T]]:
            // PackedScene is the canonical resource type for scenes; hint_string restricts further.
            override def hint                                = PropertyHint.ResourceType
            override def className                           = "PackedScene"
            def variantType                                  = VariantType.Object
            def write(dest: Ptr[Byte], value: Tscn[T]): Unit =
                !(dest.asInstanceOf[Ptr[Int]]) = VariantType.Object
                !(dest + 8).asInstanceOf[Ptr[Ptr[Byte]]] =
                    if value.isNull then null else value.objectPtr
            end write
            def read(src: Ptr[Byte]): Tscn[T] =
                new Gd[T](!(src + 8).asInstanceOf[Ptr[Ptr[Byte]]], gc)
end Tscn

/** Wraps a nullable exported value and prevents the inspector from showing `<empty>`.
  *
  * In Godot's inspector, an `Option[A]` or nullable reference shows as removable. `Required[A]`
  * marks a field as non-nullable — the inspector will not offer a "clear" button.
  *
  * {{{
  * @gdexport var target: Required[Gd[Node2D]] = Required(Gd.fromHandle(null))
  * }}}
  */
opaque type Required[A] = A

object Required:
    def apply[A](value: A): Required[A] = value

    extension [A](r: Required[A]) def value: A = r

    given exportTypeRequired: [A] => (inner: ExportType[A]) => ExportType[Required[A]] =
        new ExportType[Required[A]]:
            def variantType         = inner.variantType
            override def hint       = inner.hint
            override def hintString = inner.hintString
            override def className  = inner.className
            // PROPERTY_USAGE_STORAGE | PROPERTY_USAGE_EDITOR — same as Default but signal "required"
            override def usage                                   = PropertyUsage.Default
            def write(dest: Ptr[Byte], value: Required[A]): Unit = inner.write(dest, value)
            def read(src: Ptr[Byte]): Required[A]                = inner.read(src)
end Required
