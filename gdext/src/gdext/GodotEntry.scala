package gdext

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

// Global extension state — set once by the entry function, read by callbacks.
private[gdext] var gdxGetProcAddress: GetProcAddressFn = scala.compiletime.uninitialized
private[gdext] var gdxLibrary: Ptr[Byte]               = null

// Stored so the GC never collects these closures while Godot holds raw pointers.
private var _initCb: CFuncPtr2[Ptr[Byte], CInt, Unit]   = scala.compiletime.uninitialized
private var _deinitCb: CFuncPtr2[Ptr[Byte], CInt, Unit] = scala.compiletime.uninitialized

object GodotEntry:
    @exported("godot_scala_init")
    def godotScalaInit(
        getProcAddress: GetProcAddressFn,
        library: Ptr[Byte],
        initPtr: Ptr[GdxInitStruct]
    ): CUnsignedChar = FileLogger.use("godot-init") { logger =>
        logger.log("Starting Scala-Native GDExtension.")

        gdxGetProcAddress = getProcAddress
        gdxLibrary = library

        // Build callbacks and keep references so they aren't GC'd.
        _initCb = CFuncPtr2.fromScalaFunction[Ptr[Byte], CInt, Unit] { (_, level) =>
            if level == GdxInitLevel.Scene then ClassRegistrar.register()
        }
        _deinitCb = CFuncPtr2.fromScalaFunction[Ptr[Byte], CInt, Unit] { (_, _) => () }

        // Fill in GDExtensionInitialization.
        initPtr._1 = GdxInitLevel.Scene // minimum level
        initPtr._2 = null               // userdata
        initPtr._3 = CFuncPtr.toPtr(_initCb).asInstanceOf[Ptr[Byte]]
        initPtr._4 = CFuncPtr.toPtr(_deinitCb).asInstanceOf[Ptr[Byte]]

        logger.log("Initialization struct written; waiting for scene-level callback.")
        1.toUByte // GDExtensionBool true = success
    }
end GodotEntry
