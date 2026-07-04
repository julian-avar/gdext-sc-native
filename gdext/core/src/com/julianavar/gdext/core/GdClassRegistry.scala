package com.julianavar.gdext.core

import com.julianavar.gdext.core.virtual.VirtualEntry
import com.julianavar.gdext.core.method.MethodEntry

/** A property registration item — either an actual exported field or an inspector section marker.
  *
  * Markers (Group, Subgroup, Category) carry no dispatch logic; they instruct Godot's ClassDB to
  * render a separator in the inspector. They must appear in declaration order before the properties
  * they govern.
  */
sealed trait PropertyItem

object PropertyItem:
    /** An exported field backed by get/set dispatch. */
    case class Prop(descriptor: PropertyDescriptor) extends PropertyItem

    /** Inspector group header (`@export_group("Name")`). */
    case class Group(name: String, prefix: String = "") extends PropertyItem

    /** Inspector subgroup header (`@export_subgroup("Name")`). */
    case class Subgroup(name: String, prefix: String = "") extends PropertyItem

    /** Inspector category header (`@export_category("Name")`). */
    case class Category(name: String) extends PropertyItem
end PropertyItem

/** Registry populated by the user's entry point before ClassRegistrar.register() runs. */
object GdClassRegistry:
    private val registrations = scala.collection.mutable.ListBuffer[GdClassRegistration]()

    /** Register a user-defined extension class.
      *
      * `virtuals` should be the `entries` from the generated `{ParentClass}Virtuals` object for the
      * Godot base class being extended (which already includes the full ancestor chain). If
      * omitted, no virtuals will be dispatched to Scala.
      *
      * `isRuntime` must be true for Node subclasses (keeps the editor from ticking _ready/_process
      * while editing) and false for Resource/Object subclasses (prevents editor placeholder issues
      * on hot-reload).
      */
    def register(
        name: String,
        parentName: String,
        factory: () => GodotObject,
        virtuals: Vector[VirtualEntry] = Vector.empty,
        properties: List[PropertyItem] = List.empty,
        methods: List[MethodEntry] = List.empty,
        signals: List[SignalDescriptor] = List.empty,
        isRuntime: Boolean = true,
        initLevel: Int = GdxInitLevel.Scene
    ): Unit = registrations += GdClassRegistration(
      name,
      parentName,
      factory,
      virtuals,
      properties,
      methods,
      signals,
      isRuntime,
      initLevel
    )

    def getRegistrations: List[GdClassRegistration] = registrations.toList
    def clear(): Unit                               = registrations.clear()

    /** Look up the canonical live Scala instance for a Godot engine object pointer.
      *
      * Used by `GodotClass.derived` wrappers so `getNode[Player]` returns the REAL `Player`
      * instance (with all its field state) rather than a fresh empty wrapper. Returns `None` for
      * pure engine objects that are not user-defined extension classes.
      */
    def lookupByPtr(ptr: scala.scalanative.unsafe.Ptr[Byte]): Option[GodotObject] = ClassRegistrar
        .instanceForGodotPtr(ptr)
end GdClassRegistry

private[gdext] case class GdClassRegistration(
    name: String,
    parentName: String,
    factory: () => GodotObject,
    virtuals: Vector[VirtualEntry],
    properties: List[PropertyItem],
    methods: List[MethodEntry],
    signals: List[SignalDescriptor],
    isRuntime: Boolean,
    initLevel: Int
)
