package gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

// Global extension state — set once by the entry function, read by callbacks.
private[gdext] var gdxGetProcAddress: GetProcAddressFn = scala.compiletime.uninitialized
private[gdext] var gdxLibrary: Ptr[Byte]               = null

// Stored so the GC never collects these closures while Godot holds raw pointers.
private var initCallback: CFuncPtr2[Ptr[Byte], CInt, Unit]   = scala.compiletime.uninitialized
private var deinitCallback: CFuncPtr2[Ptr[Byte], CInt, Unit] = scala.compiletime.uninitialized
private var _onSceneInit: () => Unit                         = null

/** Library-side initialisation logic.
  *
  * Users must expose `godot_scala_init` themselves and call this from it:
  *
  * {{{
  * object MyEntry:
  *   @exported("godot_scala_init")
  *   def godotScalaInit(
  *       getProcAddress: GetProcAddressFn,
  *       library: Ptr[Byte],
  *       initPtr: Ptr[GdxInitStruct]
  *   ): CUnsignedChar =
  *     GdClassRegistry.register("SpinningCube", "Node3D", () => new SpinningCube())
  *     GodotEntry.init(getProcAddress, library, initPtr)
  * }}}
  */
object GodotEntry:
    def init(
        getProcAddress: GetProcAddressFn,
        library: Ptr[Byte],
        initPtr: Ptr[GdxInitStruct],
        onSceneInit: () => Unit = null
    ): CUnsignedChar = FileLogger.use("godot-init.log") { logger =>
        logger.log("Starting Scala-Native GDExtension.")

        gdxGetProcAddress = getProcAddress
        gdxLibrary = library
        _onSceneInit = onSceneInit

        GdxApi.initialize(getProcAddress)

        initCallback = CFuncPtr2.fromScalaFunction[Ptr[Byte], CInt, Unit] { (_, level) =>
            if level == GdxInitLevel.Scene then
                ClassRegistrar.register(GdxInitLevel.Scene)
                if _onSceneInit != null then _onSceneInit()
            else if level == GdxInitLevel.Editor then ClassRegistrar.register(GdxInitLevel.Editor)
        }
        deinitCallback = CFuncPtr2.fromScalaFunction[Ptr[Byte], CInt, Unit] { (_, level) =>
            if level == GdxInitLevel.Editor then ClassRegistrar.unregisterAll(GdxInitLevel.Editor)
            else if level == GdxInitLevel.Scene then
                ClassRegistrar.unregisterAll(GdxInitLevel.Scene)
        }

        // Request Editor-level init so @tool classes are also registered.
        initPtr._1 = GdxInitLevel.Editor
        initPtr._2 = null
        initPtr._3 = CFuncPtr.toPtr(initCallback).asInstanceOf[Ptr[Byte]]
        initPtr._4 = CFuncPtr.toPtr(deinitCallback).asInstanceOf[Ptr[Byte]]

        logger.log("Initialization struct written; waiting for scene-level callback.")
        1.toUByte
    }
end GodotEntry
