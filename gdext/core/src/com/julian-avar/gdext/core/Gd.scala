package com.`julian-avar`.gdext.core

import scala.scalanative.unsafe.*

/** Typed reference to a Godot engine object.
  *
  * Carries the raw handle and the `GodotClass[T]` evidence needed to manage lifetime and cast. Two
  * lifetime regimes, selected by `GodotClass[T].isRefCounted`:
  *
  *   - **Manually-managed** (Object/Node subtree): the object lives until freed. Call [[free]] to
  *     destroy it via `object_destroy`. Nodes added to the scene tree are owned by their parent —
  *     do NOT free those.
  *   - **Reference-counted** (RefCounted subtree, e.g. Resource): call [[unref]] to drop one
  *     reference. [[newInstance]] initialises the refcount so the returned `Gd[T]` holds the first
  *     reference.
  *
  * {{{
  * val btn: Gd[Button] = getNode("Button").cast[Button]
  * btn.get.setDisabled(true)
  * }}}
  */
final class Gd[T <: GodotObject] private[core] (
    private[core] val handle: Ptr[Byte],
    private[core] val cls: GodotClass[T]
) extends AutoCloseable:
    /** The raw engine object pointer. Null when [[isNull]]. */
    def objectPtr: Ptr[Byte] = handle

    def isNull: Boolean = handle == null

    /** Unwrap to the Scala wrapper for direct method calls.
      *
      * Creates a fresh Scala instance wrapping this engine object on every call — the engine object
      * itself is not duplicated. Throws if null.
      */
    def get: T =
        if isNull then throw new NullPointerException(s"Gd[${cls.className}].get on null reference")
        else cls.wrap(handle)

    /** Stable engine instance ID (0 when null). */
    def instanceId: Long = if isNull then 0L else GdxApi.objectGetInstanceId(handle)

    /** Safe downcast to `U`. Returns a null `Gd[U]` if the object is not a `U`. */
    def cast[U <: GodotObject](using target: GodotClass[U]): Gd[U] =
        if isNull then Gd.nullOf[U]
        else
            val tag    = GdxApi.getClassTag(StringNames.cached(target.className))
            val casted = GdxApi.objectCastTo(handle, tag)
            if casted == null then Gd.nullOf[U] else new Gd[U](casted, target)

    /** Destroy a manually-managed object (Object/Node subtree). No-op for RefCounted or null. */
    def free(): Unit = if !isNull && !cls.isRefCounted then GdxApi.objectDestroy(handle)

    /** Drop one reference on a RefCounted object. No-op for non-RefCounted or null. */
    def unref(): Unit =
        if !isNull && cls.isRefCounted then Gd.callRefMethod(GdxApi.unreferenceMethodBind, handle)

    /** Implement `AutoCloseable` so `Using(gd) { ... }` works for both lifetime regimes:
      *   - RefCounted: drops one reference via [[unref]]
      *   - Manually-managed: destroys the object via [[free]]
      */
    def close(): Unit = if cls.isRefCounted then unref() else free()

    def toOpt: Option[Gd[T]] = if isNull then None else Some(this)

    override def toString: String =
        if isNull then s"Gd[${cls.className}](null)" else s"Gd[${cls.className}]#$instanceId"
end Gd

object Gd:
    /** Wrap a raw engine handle (no ownership change). */
    def fromHandle[T <: GodotObject](handle: Ptr[Byte])(using cls: GodotClass[T]): Gd[T] =
        new Gd[T](handle, cls)

    /** The null reference for type `T`. */
    def nullOf[T <: GodotObject](using cls: GodotClass[T]): Gd[T] = new Gd[T](null, cls)

    /** Construct a new engine object of class `T` and take ownership.
      *
      * For RefCounted classes calls `init_ref` so the returned `Gd[T]` holds the first reference.
      */
    def newInstance[T <: GodotObject](using cls: GodotClass[T]): Gd[T] =
        val handle = GdxApi.constructObject(StringNames.cached(cls.className).asInstanceOf[CString])
        if cls.isRefCounted && handle != null then callRefMethod(GdxApi.initRefMethodBind, handle)
        new Gd[T](handle, cls)
    end newInstance

    private[core] def callRefMethod(methodBind: Ptr[Byte], handle: Ptr[Byte]): Unit =
        if methodBind != null then Ptrcall.callVoid0(methodBind, handle)
end Gd
