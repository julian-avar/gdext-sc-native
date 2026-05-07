package example

import gdext.core.*
import scala.scalanative.unsafe.*
import gdext.generated.CenterContainerVirtuals

/** GDExtension entry point — owned by the user project, not the library.
  *
  * Binds are loaded lazily on first method call — no explicit loadBinds() needed.
  */
object GodotEntry:
    @exported("godot_scala_init")
    def godotScalaInit(
        getProcAddress: GetProcAddressFn,
        library: Ptr[Byte],
        initPtr: Ptr[GdxInitStruct]
    ): CUnsignedChar =
        GdClassRegistry.register(
          "ExampleSceneScala",
          "CenterContainer",
          () => new ExampleSceneScala(),
          CenterContainerVirtuals.entries
        )
        gdext.core.GodotEntry.init(getProcAddress, library, initPtr)
    end godotScalaInit
end GodotEntry
