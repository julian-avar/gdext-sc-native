package example

import gdext.*
import scala.scalanative.unsafe.*
import gdext.generated.UtilityFunctions

/** GDExtension entry point — owned by the user project, not the library.
  *
  * Add one GdClassRegistry.register(...) line per @gdclass class. Future: the Mill `generate()`
  * task will auto-generate this file.
  */
object GodotEntry:
    @exported("godot_scala_init")
    def godotScalaInit(
        getProcAddress: GetProcAddressFn,
        library: Ptr[Byte],
        initPtr: Ptr[GdxInitStruct]
    ): CUnsignedChar =
        println("Registering ExampleSceneScala")
        GdClassRegistry
            .register("ExampleSceneScala", "CenterContainer", () => new ExampleSceneScala())
        println("Registered ExampleSceneScala")

        // Initialize Godot first
        println("Initializing Godot...")
        val initResult = gdext.GodotEntry.init(getProcAddress, library, initPtr)
        println("Godot initialized")

        // Now load utility function bindings
        println("Loading utility function bindings")
        try
            println("About to load bindings...")
            UtilityFunctions.Binds.loadBinds()
            println("Bindings loaded")
            println(s"Print binding: ${UtilityFunctions.Binds.print}")
            if UtilityFunctions.Binds.print == null then
                println("ERROR: Print binding is null after loading!")
            else println("Print binding loaded successfully")
        catch
            case e: Exception =>
                println(s"Exception loading bindings: $e")
                e.printStackTrace()
        end try

        initResult
    end godotScalaInit
end GodotEntry
