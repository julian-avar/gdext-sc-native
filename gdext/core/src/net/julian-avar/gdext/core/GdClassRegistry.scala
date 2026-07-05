package net.`julian-avar`.gdext.core

import net.`julian-avar`.gdext.core.virtual.VirtualEntry
import net.`julian-avar`.gdext.core.method.MethodEntry

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
        initLevel: Int = GdxInitLevel.Scene,
        scriptCallMethods: List[MethodEntry] = List.empty
    ): Unit = registrations += GdClassRegistration(
      name,
      parentName,
      factory,
      virtuals,
      properties,
      methods,
      signals,
      isRuntime,
      initLevel,
      scriptCallMethods
    )

    def getRegistrations: List[GdClassRegistration] = registrations.toList
    def clear(): Unit                               = registrations.clear()

    /** Look up the canonical live Scala instance for a Godot engine object pointer.
      *
      * Used by `GodotClass.derived` wrappers so `getNode[Player]` returns the REAL `Player`
      * instance (with all its field state) rather than a fresh empty wrapper. Returns `None` for
      * pure engine objects that are not user-defined extension classes. Checks both the native
      * ClassDB registrar (`type=` on a node) and the script-instance registrar (attached script on
      * a generic node), since a live instance may have been created via either path.
      *
      * If both fast map lookups miss, falls back to a linear scan over live instances matching on
      * `.ptr` — necessary because `classdb_construct_object` returns a different pointer than the
      * parent-class engine pointer stored in the registrar's godotPtrMap when Godot forwards the
      * object as a virtual-call argument (e.g. in `ResourceFormatSaver._save`).
      */
    def lookupByPtr(ptr: scala.scalanative.unsafe.Ptr[Byte]): Option[GodotObject] = ClassRegistrar
        .instanceForGodotPtr(ptr).orElse(ScriptInstanceRegistrar.instanceForGodotPtr(ptr))
        .orElse(ClassRegistrar.findInstanceByPtrFallback(ptr))
        .orElse(ScriptInstanceRegistrar.findInstanceByPtrFallback(ptr))
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
    initLevel: Int,
    // Variant-marshalled MethodEntry per user-overridden lifecycle virtual (e.g. "_ready",
    // "_process"), registered under Godot's own underscored name. Used by ScriptInstanceRegistrar
    // when this class is attached as a Script to a generic node rather than registered as that
    // node's native ClassDB type — Godot's script-instance call_func path invokes lifecycle
    // virtuals by name with Variant-boxed args, not through the ptrcall-convention virtual table
    // `virtuals` feeds for the ClassDB path. Kept separate from `methods` so lifecycle virtuals
    // aren't double-registered as real ClassDB methods.
    scriptCallMethods: List[MethodEntry] = List.empty
)
