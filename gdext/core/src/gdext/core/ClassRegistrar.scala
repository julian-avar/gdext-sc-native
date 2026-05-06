package gdext

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*

/** Registers user-defined classes with Godot at scene-level init time. */
object ClassRegistrar:
    // godotPtr → Scala instance; looked up on every virtual call.
    private var instanceMap: Map[Ptr[Byte], GodotClass] = Map.empty

    // Keep all closures alive — GC must never collect them while Godot holds raw ptrs.
    private var freeFns: Map[String, FreeInstanceFn]     = Map.empty
    private var getVirtualFns: Map[String, GetVirtualFn] = Map.empty

    // Shared create function — one CFuncPtr for all classes; per-class data via class_userdata.
    // Tuple: (factory, parentNameBuf, classNameBuf) — both StringName buffers are heap-allocated.
    private var factoryMap: Map[Ptr[Byte], (() => GodotClass, Ptr[Byte], Ptr[Byte])] = Map.empty
    private var _createFn: CreateInstanceFn                            = null

    // Shared CallVirtualFn pointers — created once, reused for every class.
    private var _readyFn: Ptr[Byte]          = null
    private var _processFn: Ptr[Byte]        = null
    private var _physicsProcessFn: Ptr[Byte] = null

    // Pre-interned StringName buffers for virtual method dispatch.
    // Godot interns StringNames: equal names share the same _Data* pointer at offset 0.
    private var _readySN:          Ptr[Byte] = null
    private var _processSN:        Ptr[Byte] = null
    private var _physicsProcessSN: Ptr[Byte] = null

    def register(): Unit =
        println("[ClassRegistrar] register() called")
        val getProcAddr = gdxGetProcAddress
        val library     = gdxLibrary

        val stringNameNewPtr  = getProcAddr(c"string_name_new_with_utf8_chars")
        val registerClass2Ptr = getProcAddr(c"classdb_register_extension_class2")
        println(s"[ClassRegistrar] stringNameNewPtr=$stringNameNewPtr registerClass2Ptr=$registerClass2Ptr")
        if stringNameNewPtr == null || registerClass2Ptr == null then
            println("[ClassRegistrar] ERROR: null proc address, aborting")
            return

        val stringNameNew  = CFuncPtr.fromPtr[StringNameNewFn](stringNameNewPtr)
        val registerClass2 = CFuncPtr.fromPtr[RegisterClass2Fn](registerClass2Ptr)

        buildSharedCreateFn()
        buildSharedVirtualFns()
        initVirtualStringNames(stringNameNew)

        for reg <- GdClassRegistry.getRegistrations do
            // Heap-allocate StringName buffers — Godot may hold these past this call.
            val classNameBuf  = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
            val parentNameBuf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
            memset(classNameBuf, 0, StringNameSize)
            memset(parentNameBuf, 0, StringNameSize)

            Zone {
                stringNameNew(classNameBuf, toCString(reg.name))
                stringNameNew(parentNameBuf, toCString(reg.parentName))
            }

            // Allocate a unique pointer to use as class_userdata key for the shared create fn.
            val userdataPtr = malloc(1)
            factoryMap += (userdataPtr -> (reg.factory, parentNameBuf, classNameBuf))

            val freeFn = CFuncPtr2.fromScalaFunction[Ptr[Byte], Ptr[Byte], Unit] {
                (_, instancePtr) =>
                    instanceMap -= instancePtr
                    free(instancePtr)
            }
            freeFns += (reg.name -> freeFn)

            val getVirtualFn = buildGetVirtualFn()
            getVirtualFns += (reg.name -> getVirtualFn)

            val info = stackalloc[ClassCreationInfo2]()
            memset(info.asInstanceOf[Ptr[Byte]], 0, sizeof[ClassCreationInfo2])
            info._1 = 0.toUByte // is_virtual
            info._2 = 0.toUByte // is_abstract
            info._3 = 1.toUByte // is_exposed
            info._15 = CFuncPtr.toPtr(_createFn).asInstanceOf[Ptr[Byte]]
            info._16 = CFuncPtr.toPtr(freeFn).asInstanceOf[Ptr[Byte]]
            info._18 = CFuncPtr.toPtr(getVirtualFn).asInstanceOf[Ptr[Byte]]
            info._22 = userdataPtr // class_userdata — passed back to _createFn

            println(s"[ClassRegistrar] calling registerClass2 for ${reg.name}")
            registerClass2(library, classNameBuf, parentNameBuf, info)
            println(s"[ClassRegistrar] registered ${reg.name}")
        end for
        println("[ClassRegistrar] register() complete")
    end register

    // ── virtual dispatch helpers ────────────────────────────────────────────

    /** Build the three shared CallVirtualFn pointers (once per extension load). */
    private def buildSharedVirtualFns(): Unit =
        if _readyFn == null then
            val fn = CFuncPtr3.fromScalaFunction[Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], Unit] {
                (instancePtr, _, _) => instanceMap.get(instancePtr).foreach(_._ready())
            }
            _readyFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _processFn == null then
            val fn = CFuncPtr3.fromScalaFunction[Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], Unit] {
                (instancePtr, args, _) =>
                    val delta = !args(0).asInstanceOf[Ptr[Double]]
                    instanceMap.get(instancePtr).foreach(_._process(delta))
            }
            _processFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _physicsProcessFn == null then
            val fn = CFuncPtr3.fromScalaFunction[Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], Unit] {
                (instancePtr, args, _) =>
                    val delta = !args(0).asInstanceOf[Ptr[Double]]
                    instanceMap.get(instancePtr).foreach(_._physicsProcess(delta))
            }
            _physicsProcessFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if
    end buildSharedVirtualFns

    /** Builds (once) the shared create function. Per-class data is stored in `factoryMap` and
      * retrieved via the `class_userdata` pointer Godot passes back as the argument.
      */
    private def buildSharedCreateFn(): Unit =
        if _createFn == null then
            _createFn = CFuncPtr1.fromScalaFunction[Ptr[Byte], Ptr[Byte]] { userdata =>
                println(s"[ClassRegistrar] create_instance_func called, userdata=$userdata")
                factoryMap.get(userdata) match
                    case Some((factory, parentNameBuf, classNameBuf)) =>
                        val godotPtr = GdxApi.constructObject(parentNameBuf)
                        println(s"[ClassRegistrar] constructed object godotPtr=$godotPtr")
                        val obj = factory()
                        obj.ptr = godotPtr
                        // Allocate a stable per-instance binding pointer.
                        // Godot stores this and passes it back as p_instance to virtual call fns.
                        val instancePtr = malloc(1).asInstanceOf[Ptr[Byte]]
                        instanceMap += (instancePtr -> obj)
                        GdxApi.setInstance(godotPtr, classNameBuf, instancePtr)
                        println(s"[ClassRegistrar] set instance binding instancePtr=$instancePtr")
                        godotPtr
                    case None =>
                        println(s"[ClassRegistrar] ERROR: no factory for userdata=$userdata")
                        null
            }
        end if
    end buildSharedCreateFn

    /** Allocate persistent StringName buffers for the known virtual method names.
      * Called once after string_name_new_with_utf8_chars is resolved.
      */
    private def initVirtualStringNames(stringNameNew: StringNameNewFn): Unit =
        if _readySN != null then return
        def alloc(name: CString): Ptr[Byte] =
            val buf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
            memset(buf, 0, StringNameSize)
            stringNameNew(buf, name)
            buf
        _readySN          = alloc(c"_ready")
        _processSN        = alloc(c"_process")
        _physicsProcessSN = alloc(c"_physics_process")
    end initVirtualStringNames

    /** get_virtual_func: receives (classUserdata, StringNamePtr), returns CallVirtualFn or null.
      *
      * StringNames are interned by Godot: the first pointer-sized word of the struct is a _Data*
      * that is shared among all StringNames with equal content. We compare that word against
      * pre-created StringName buffers to identify the method without any string conversion API.
      */
    private def buildGetVirtualFn(): GetVirtualFn = CFuncPtr2
        .fromScalaFunction[Ptr[Byte], Ptr[Byte], Ptr[Byte]] { (_, namePtr) =>
            val data = !(namePtr.asInstanceOf[Ptr[Long]])
            val readyData = !(_readySN.asInstanceOf[Ptr[Long]])
            println(s"[ClassRegistrar] get_virtual_func: nameData=$data readySNData=$readyData")
            if data == readyData then _readyFn
            else if data == !(_processSN.asInstanceOf[Ptr[Long]]) then _processFn
            else if data == !(_physicsProcessSN.asInstanceOf[Ptr[Long]]) then _physicsProcessFn
            else null
        }

    // ── string helpers ──────────────────────────────────────────────────────

    private def toCString(s: String)(using Zone): CString = scalanative.unsafe.toCString(s)
end ClassRegistrar
