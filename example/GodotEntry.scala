package mygame

import gdext.*
import scala.scalanative.unsafe.*

/** GDExtension entry point — owned by the user project, not the library.
  *
  * Add one GdClassRegistry.register(...) line per @gdclass class.
  * Future: the Mill `generate()` task will auto-generate this file.
  */
object GodotEntry:
    @exported("godot_scala_init")
    def godotScalaInit(
        getProcAddress: GetProcAddressFn,
        library: Ptr[Byte],
        initPtr: Ptr[GdxInitStruct]
    ): CUnsignedChar =
        GdClassRegistry.register("SpinningCube", "Node3D", () => new SpinningCube())
        gdext.GodotEntry.init(getProcAddress, library, initPtr)
end GodotEntry
