package example

import gdext.core.*
import scala.scalanative.unsafe.*
import gdext.generated.CenterContainerVirtuals
import gdext.generated.CharacterBody2DVirtuals
import gdext.generated.InputMap
import example.rigid_body_example.PlayerSc

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
        GdClassRegistry.register(
          "PlayerSc",
          "CharacterBody2D",
          () => new PlayerSc(),
          CharacterBody2DVirtuals.entries
        )
        gdext.core.GodotEntry.init(getProcAddress, library, initPtr, () => {
            val inputMap = new InputMap(GdxApi.getSingleton(c"InputMap"))
            inputMap.loadFromProjectSettings()
        })
    end godotScalaInit
end GodotEntry
