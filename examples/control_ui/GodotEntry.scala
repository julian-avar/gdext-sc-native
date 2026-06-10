package example

import gdext.core.*
import scala.scalanative.unsafe.*
import gdext.generated.CenterContainerVirtuals
import example.control_ui_example.ExampleSceneScala
import gdext.core.method.MethodEntry

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
          CenterContainerVirtuals.entries,
          methods = List(MethodEntry(
            "_on_button_pressed",
            (obj, _, _) => obj.asInstanceOf[ExampleSceneScala]._onButtonPressed()
          ))
        )
        gdext.core.GodotEntry.init(getProcAddress, library, initPtr)
    end godotScalaInit
end GodotEntry
