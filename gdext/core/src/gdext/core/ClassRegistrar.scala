package gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*

/** Registers user-defined classes with Godot at scene-level init time. */
object ClassRegistrar:
    // godotPtr → Scala instance; looked up on every virtual call.
    private var instanceMap: Map[Ptr[Byte], GodotObject] = Map.empty

    // Keep all closures alive — GC must never collect them while Godot holds raw ptrs.
    private var freeFns: Map[String, FreeInstanceFn] = Map.empty

    // Shared create function — one CFuncPtr for all classes; per-class data via class_userdata.
    private var factoryMap: Map[Ptr[Byte], (() => GodotObject, Ptr[Byte], Ptr[Byte])] = Map.empty
    private var _createFn: CreateInstanceFn                                           = null

    // ── virtual dispatch (fields 19 + 20 of ClassCreationInfo2) ────────────────
    //
    // Scala Native forbids CFuncPtr lambdas from closing over local / parameter state.
    // We work around this by using two SHARED CFuncPtrs backed entirely by singleton maps:
    //
    //   field 19  get_virtual_call_data_func(classUserdata, namePtr) → Ptr[Byte]
    //     Looks up virtualTables[classUserdata] and returns a pre-allocated Ptr[Int]
    //     containing the dispatchId for that virtual, or null if not overridden.
    //
    //   field 20  call_virtual_with_data_func(instancePtr, _, callData, args, ret)
    //     Reads the dispatchId from callData, looks up the Scala dispatch fn in
    //     dispatchFns, and calls it with the GodotObject from instanceMap.
    //
    // Both functions reference only singleton fields → no local-capture restriction.

    // userdataPtr → Array[(internedNameData, callDataBuf)]
    // callDataBuf: heap-allocated Ptr[Int] holding the dispatchId (pre-allocated at
    // registration time, never freed — Godot may hold a reference for the extension lifetime).
    private var virtualTables: Map[Ptr[Byte], Array[(Long, Ptr[Byte])]] = Map.empty

    // dispatchId → Scala dispatch function (GodotObject, args, ret) => Unit
    private val dispatchFns = scala.collection.mutable
        .Map[Int, (GodotObject, Ptr[Ptr[Byte]], Ptr[Byte]) => Unit]()
    private var dispatchNextId = 0

    // The two shared virtual-dispatch CFuncPtrs.  Stored in singleton fields so the GC
    // never collects them while Godot holds the native function pointers.
    private var _getCallDataFn: Ptr[Byte]  = null
    private var _callWithDataFn: Ptr[Byte] = null

    def register(): Unit =
        println("[ClassRegistrar] register() called")
        val getProcAddr = gdxGetProcAddress
        val library     = gdxLibrary

        val stringNameNewPtr  = getProcAddr(c"string_name_new_with_utf8_chars")
        val registerClass2Ptr = getProcAddr(c"classdb_register_extension_class2")
        println(
          s"[ClassRegistrar] stringNameNewPtr=$stringNameNewPtr registerClass2Ptr=$registerClass2Ptr"
        )
        if stringNameNewPtr == null || registerClass2Ptr == null then
            println("[ClassRegistrar] ERROR: null proc address, aborting")
            return

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

            // Allocate a unique pointer for class_userdata — doubles as the virtualTables key.
            val userdataPtr = malloc(1)
            factoryMap += (userdataPtr -> (reg.factory, parentNameBuf, classNameBuf))

            val freeFn = CFuncPtr2
                .fromScalaFunction[Ptr[Byte], Ptr[Byte], Unit] { (_, instancePtr) =>
                    instanceMap -= instancePtr
                    free(instancePtr)
                }
            freeFns += (reg.name -> freeFn)

            buildVirtualTable(reg.virtuals, stringNameNew, userdataPtr)

            val info = stackalloc[ClassCreationInfo2]()
            memset(info.asInstanceOf[Ptr[Byte]], 0, sizeof[ClassCreationInfo2])
            info._1 = 0.toUByte // is_virtual
            info._2 = 0.toUByte // is_abstract
            info._3 = 1.toUByte // is_exposed
            info._4 = null      // set_func              (future: @export properties)
            info._5 = null      // get_func              (future: @export properties)
            info._6 = null      // get_property_list_func
            info._7 = null      // free_property_list_func
            info._8 = null      // property_can_revert_func
            info._9 = null      // property_get_revert_func
            info._10 = null     // validate_property_func
            info._11 = null     // notification_func
            info._12 = null     // to_string_func
            info._13 = null     // reference_func        (future: RefCounted safety)
            info._14 = null     // unreference_func      (future: RefCounted safety)
            info._15 = CFuncPtr.toPtr(_createFn).asInstanceOf[Ptr[Byte]]
            info._16 = CFuncPtr.toPtr(freeFn).asInstanceOf[Ptr[Byte]]
            info._17 = null // recreate_instance_func
            info._18 = null // get_virtual_func      (using 19+20 instead)
            info._19 = _getCallDataFn
            info._20 = _callWithDataFn
            info._21 = null        // get_rid_func
            info._22 = userdataPtr // class_userdata — key for virtualTables lookup

            println(s"[ClassRegistrar] calling registerClass2 for ${reg.name}")
            registerClass2(library, classNameBuf, parentNameBuf, info)
            println(s"[ClassRegistrar] registered ${reg.name}")
        end for
        println("[ClassRegistrar] register() complete")
    end register

    // ── virtual dispatch helpers ────────────────────────────────────────────

    /** Intern each virtual's StringName, store the dispatch fn in the global registry, and
      * pre-allocate a Ptr[Int] callDataBuf per entry. Stored in `virtualTables` keyed by
      * `userdataPtr` so the shared CFuncPtrs can find it without any local captures.
      */
    private def buildVirtualTable(
        virtuals: Vector[gdext.core.virtual.VirtualEntry],
        stringNameNew: StringNameNewFn,
        userdataPtr: Ptr[Byte]
    ): Unit =
        val table = virtuals.map { entry =>
            val snBuf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
            memset(snBuf, 0, StringNameSize)
            Zone { stringNameNew(snBuf, toCString(entry.name)) }
            val internData = !(snBuf.asInstanceOf[Ptr[Long]])

            // Register the Scala dispatch fn; store its ID in a heap buffer for Godot.
            val id = dispatchNextId; dispatchNextId += 1
            dispatchFns(id) = entry.dispatch
            val callDataBuf = malloc(4).asInstanceOf[Ptr[Int]]
            !callDataBuf = id

            (internData, callDataBuf.asInstanceOf[Ptr[Byte]])
        }.toArray
        virtualTables += (userdataPtr -> table)
    end buildVirtualTable

    /** Build the two shared virtual-dispatch CFuncPtrs (once per extension load).
      *
      * These are assigned to ClassCreationInfo2 fields 19 and 20 for every registered class. They
      * only reference singleton fields, satisfying Scala Native's no-local-capture rule.
      */
    private def buildSharedVirtualFns(): Unit =
        if _getCallDataFn == null then
            val fn = CFuncPtr2
                .fromScalaFunction[Ptr[Byte], Ptr[Byte], Ptr[Byte]] { (classUserdata, namePtr) =>
                    val nameData = !(namePtr.asInstanceOf[Ptr[Long]])
                    virtualTables.get(classUserdata) match
                        case Some(table) =>
                            var i                = 0
                            var found: Ptr[Byte] = null
                            while i < table.length && found == null do
                                if table(i)._1 == nameData then found = table(i)._2
                                i += 1
                            found
                        case None => null
                    end match
                }
            _getCallDataFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _callWithDataFn == null then
            val fn = CFuncPtr5
                .fromScalaFunction[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Ptr[Byte]], Ptr[
                  Byte
                ], Unit] { (instancePtr, _, callData, args, ret) =>
                    val id = !callData.asInstanceOf[Ptr[Int]]
                    dispatchFns.get(id).foreach { dispatch =>
                        instanceMap.get(instancePtr).foreach(dispatch(_, args, ret))
                    }
                }
            _callWithDataFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if
    end buildSharedVirtualFns

    // ── create / free ───────────────────────────────────────────────────────

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
                        val instancePtr = malloc(1).asInstanceOf[Ptr[Byte]]
                        instanceMap += (instancePtr -> obj)
                        GdxApi.setInstance(godotPtr, classNameBuf, instancePtr)
                        println(s"[ClassRegistrar] set instance binding instancePtr=$instancePtr")
                        godotPtr
                    case None =>
                        println(s"[ClassRegistrar] ERROR: no factory for userdata=$userdata")
                        null
                end match
            }
        end if
    end buildSharedCreateFn

    // ── string helpers ──────────────────────────────────────────────────────

    private def toCString(s: String)(using Zone): CString = scalanative.unsafe.toCString(s)
end ClassRegistrar
