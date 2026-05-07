package gdext

/** Registry populated by the user's entry point before ClassRegistrar.register() runs. */
object GdClassRegistry:
    private val registrations = scala.collection.mutable.ListBuffer[GdClassRegistration]()

    def register(name: String, parentName: String, factory: () => GodotObject): Unit =
        registrations += GdClassRegistration(name, parentName, factory)

    def getRegistrations: List[GdClassRegistration] = registrations.toList
    def clear(): Unit                               = registrations.clear()
end GdClassRegistry

private[gdext] case class GdClassRegistration(
    name: String,
    parentName: String,
    factory: () => GodotObject
)
