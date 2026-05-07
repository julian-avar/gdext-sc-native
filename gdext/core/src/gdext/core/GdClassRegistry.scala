package gdext.core

import gdext.core.virtual.VirtualEntry

/** Registry populated by the user's entry point before ClassRegistrar.register() runs. */
object GdClassRegistry:
    private val registrations = scala.collection.mutable.ListBuffer[GdClassRegistration]()

    /** Register a user-defined extension class.
      *
      * `virtuals` should be the `entries` from the generated `{ParentClass}Virtuals` object for the
      * Godot base class being extended (which already includes the full ancestor chain). If
      * omitted, no virtuals will be dispatched to Scala.
      */
    def register(
        name: String,
        parentName: String,
        factory: () => GodotObject,
        virtuals: Vector[VirtualEntry] = Vector.empty
    ): Unit = registrations += GdClassRegistration(name, parentName, factory, virtuals)

    def getRegistrations: List[GdClassRegistration] = registrations.toList
    def clear(): Unit                               = registrations.clear()
end GdClassRegistry

private[gdext] case class GdClassRegistration(
    name: String,
    parentName: String,
    factory: () => GodotObject,
    virtuals: Vector[VirtualEntry]
)
