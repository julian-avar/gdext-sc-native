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
    private var factoryMap: Map[Ptr[Byte], (() => GodotClass, String)] = Map.empty
    private var _createFn: CreateInstanceFn                            = null

    // Shared CallVirtualFn pointers — created once, reused for every class.
    private var _readyFn: Ptr[Byte]          = null
    private var _processFn: Ptr[Byte]        = null
    private var _physicsProcessFn: Ptr[Byte] = null

    def register(): Unit =
        val getProcAddr = gdxGetProcAddress
        val library     = gdxLibrary

        val stringNameNewPtr  = getProcAddr(c"string_name_new_with_utf8_chars")
        val registerClass2Ptr = getProcAddr(c"classdb_register_extension_class2")
        if stringNameNewPtr == null || registerClass2Ptr == null then return

        val stringNameNew  = CFuncPtr.fromPtr[StringNameNewFn](stringNameNewPtr)
        val registerClass2 = CFuncPtr.fromPtr[RegisterClass2Fn](registerClass2Ptr)

        buildSharedCreateFn()
        buildSharedVirtualFns()

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
            factoryMap += (userdataPtr -> (reg.factory, reg.parentName))

            val freeFn = CFuncPtr2.fromScalaFunction[Ptr[Byte], Ptr[Byte], Unit] {
                (_, instancePtr) => instanceMap -= instancePtr
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

            registerClass2(library, classNameBuf, parentNameBuf, info)
        end for
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
                factoryMap.get(userdata) match
                    case Some((factory, parentName)) => Zone {
                            val godotPtr = GdxApi.constructObject(toCString(parentName))
                            val obj      = factory()
                            obj.ptr = godotPtr
                            instanceMap += (godotPtr -> obj)
                            godotPtr
                        }
                    case None => null
            }
        end if
    end buildSharedCreateFn

    /** get_virtual_func: receives (classUserdata, StringNamePtr), returns CallVirtualFn or null.
      *
      * NOTE: Godot's StringName is opaque — reading it as a plain C string is incorrect. This uses
      * a best-effort byte-peek that works for short ASCII method names in practice. A proper fix
      * requires calling string_name_to_utf8_chars via GdxApi.
      */
    private def buildGetVirtualFn(): GetVirtualFn = CFuncPtr2
        .fromScalaFunction[Ptr[Byte], Ptr[Byte], Ptr[Byte]] { (_, namePtr) =>
            peekStringName(namePtr) match
                case "_ready"           => _readyFn
                case "_process"         => _processFn
                case "_physics_process" => _physicsProcessFn
                case _                  => null
        }

    // ── string helpers ──────────────────────────────────────────────────────

    private def toCString(s: String)(using Zone): CString = scalanative.unsafe.toCString(s)

    /** Best-effort peek of a Godot StringName as an ASCII string. Works only because short method
      * names happen to be stored inline on this platform. TODO: replace with
      * string_name_to_utf8_chars GDExtension API call.
      */
    private def peekStringName(ptr: Ptr[Byte]): String =
        val sb = new StringBuilder
        var i  = 0
        while i < 256 do
            val b = !(ptr + i)
            if b == 0.toByte then return sb.toString()
            sb.append(b.toChar)
            i += 1
        end while
        sb.toString()
    end peekStringName
end ClassRegistrar
