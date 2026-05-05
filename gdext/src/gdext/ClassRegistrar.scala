package gdext

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*

object ClassRegistrar:
    // Persistent storage — malloc'd, outlives any Zone.
    private var nodeStringName: Ptr[Byte]  = null // StringName("Node")
    private var readyStringName: Ptr[Byte] = null // StringName("_ready")

    // Kept alive so the GC never collects the closures while Godot holds pointers.
    private var _createFn: CreateInstanceFn = scala.compiletime.uninitialized
    private var _freeFn: FreeInstanceFn     = scala.compiletime.uninitialized
    private var _readyFn: CallVirtualFn     = scala.compiletime.uninitialized
    private var _getVirtualFn: GetVirtualFn = scala.compiletime.uninitialized

    def register(): Unit =
        val getProcAddr = gdxGetProcAddress
        val library     = gdxLibrary

        // ── Get required proc addresses (c"..." are static, no Zone needed) ──
        val stringNameNewPtr  = getProcAddr(c"string_name_new_with_utf8_chars")
        val registerClass2Ptr = getProcAddr(c"classdb_register_extension_class2")

        if stringNameNewPtr == null || registerClass2Ptr == null then
            return

        val stringNameNew  = CFuncPtr.fromPtr[StringNameNewFn](stringNameNewPtr)
        val registerClass2 = CFuncPtr.fromPtr[RegisterClass2Fn](registerClass2Ptr)

        // ── StringName("Node") — malloc so it outlives this stack frame ───────
        nodeStringName = malloc(StringNameSize)
        memset(nodeStringName, 0, StringNameSize)
        stringNameNew(nodeStringName, c"Node")

        // ── StringName("_ready") ──────────────────────────────────────────────
        readyStringName = malloc(StringNameSize)
        memset(readyStringName, 0, StringNameSize)
        stringNameNew(readyStringName, c"_ready")

        // ── StringName("ScalaNode") — only needed during registration call ────
        val classNameBuf = stackalloc[Byte](StringNameSize)
        memset(classNameBuf, 0, StringNameSize)
        stringNameNew(classNameBuf, c"ScalaNode")

        // ── Closures (stored to prevent GC collection) ────────────────────────
        _createFn = CFuncPtr1.fromScalaFunction[Ptr[Byte], Ptr[Byte]] { _ =>
            GdxApi.constructObject(c"Node")
        }
        _freeFn = CFuncPtr2.fromScalaFunction[Ptr[Byte], Ptr[Byte], Unit] { (_, _) => () }

        _readyFn = CFuncPtr3
            .fromScalaFunction[Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], Unit] { (_, _, _) =>
                FileLogger.use("godot-ready") { logger => logger.log("ScalaNode._ready called!") }
            }

        _getVirtualFn = CFuncPtr2.fromScalaFunction[Ptr[Byte], Ptr[Byte], Ptr[Byte]] { (_, name) =>
            if memcmp(name, readyStringName, StringNameSize) == 0 then
                CFuncPtr.toPtr(_readyFn).asInstanceOf[Ptr[Byte]]
            else null
        }

        // ── GDExtensionClassCreationInfo2 — stack-allocated, only needs to   ──
        // ── survive until classdb_register_extension_class2 returns.          ──
        val info = stackalloc[ClassCreationInfo2]()
        memset(info.asInstanceOf[Ptr[Byte]], 0, sizeof[ClassCreationInfo2])

        info._1 = 0.toUByte // is_virtual  = false
        info._2 = 0.toUByte // is_abstract = false
        info._3 = 1.toUByte // is_exposed  = true
        info._15 = CFuncPtr.toPtr(_createFn)
            .asInstanceOf[Ptr[Byte]] // create_instance_func (required)
        info._16 = CFuncPtr.toPtr(_freeFn)
            .asInstanceOf[Ptr[Byte]] // free_instance_func   (required)
        info._18 = CFuncPtr.toPtr(_getVirtualFn).asInstanceOf[Ptr[Byte]] // get_virtual_func

        registerClass2(library, classNameBuf, nodeStringName, info)
    end register
end ClassRegistrar
