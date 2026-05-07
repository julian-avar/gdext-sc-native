package example

import gdext.*
import scala.scalanative.unsafe.*
import gdext.generated.{UtilityFunctions, Node, CanvasItem, Object as GdObject}

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

        val initResult = gdext.GodotEntry.init(getProcAddress, library, initPtr, () => {
            UtilityFunctions.Binds.loadBinds()
            Node.Binds.loadBinds()
            CanvasItem.Binds.loadBinds()
            GdObject.Binds.loadBinds()
        })

        initResult
    end godotScalaInit
end GodotEntry
