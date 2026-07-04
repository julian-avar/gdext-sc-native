package net.`julian-avar`.gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*
import net.`julian-avar`.gdext.core.method.MethodEntry
import net.`julian-avar`.gdext.core.virtual.VirtualEntry

/** Registers user-defined classes with Godot at scene-level init time. */
private[gdext] object ClassRegistrar:
    // instancePtr → Scala instance; looked up on every virtual/property/method dispatch.
    private var instanceMap: Map[Ptr[Byte], GodotObject] = Map.empty

    // godotPtr (engine object pointer) → Scala instance.
    // Populated in create/recreate; evicted in free.  Lets GodotClass.wrap return the
    // canonical Scala instance rather than a fresh empty wrapper.
    private var godotPtrMap: Map[Ptr[Byte], GodotObject] = Map.empty

    // instancePtr → godotPtr; needed so the free callback can evict the godotPtrMap entry.
    private var instanceToGodotPtr: Map[Ptr[Byte], Ptr[Byte]] = Map.empty

    // Keep all closures alive — GC must never collect them while Godot holds raw ptrs.
    private var freeFns: Map[String, FreeInstanceFn] = Map.empty

    // Shared create function — one CFuncPtr for all classes; per-class data via class_userdata.
    private var factoryMap: Map[Ptr[Byte], (() => GodotObject, Ptr[Byte], Ptr[Byte])] = Map.empty
    private var _createFn: CreateInstanceFn                                           = null
    private var _recreateFn: RecreateInstanceFn                                       = null

    // ── virtual dispatch (fields 19 + 20 of ClassCreationInfo2) ────────────────
    //
    // Scala Native forbids CFuncPtr lambdas from closing over local / parameter state.
    // We work around this by using two SHARED CFuncPtrs backed entirely by singleton maps:
    //
    //   field 19  get_virtual_call_data_func(classUserdata, namePtr) → Ptr[Byte]
    //     Looks up virtualTables[classUserdata] and returns a pre-allocated Ptr[Int]
    //     containiCStruct25ng the dispatchId for that virtual, or null if not overridden.
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

    // ── property dispatch (fields 4 + 5 of ClassCreationInfo2) ──────────────
    //
    // Same singleton-map pattern as virtual dispatch.
    //
    //   field 4  set_func(instancePtr, namePtr, valueVariantPtr) → Bool
    //   field 5  get_func(instancePtr, namePtr, retVariantPtr)   → Bool
    //
    // instanceClassMap: instancePtr → userdataPtr (lets set/get look up the class's property table)
    // propertyTables:   userdataPtr → Array[(internedName, PropertyDescriptor)]

    private var instanceClassMap: Map[Ptr[Byte], Ptr[Byte]]                       = Map.empty
    private var propertyTables: Map[Ptr[Byte], Array[(Long, PropertyDescriptor)]] = Map.empty
    private var _setFn: Ptr[Byte]                                                 = null
    private var _getFn: Ptr[Byte]                                                 = null

    // ── method dispatch (classdb_register_extension_class_method) ────────────
    //
    // Same singleton-map pattern as virtual dispatch.
    //
    //   call_func(methodUserdata, instancePtr, args, argCount, rReturn, rError)
    //     methodUserdata is a pre-allocated Ptr[Int] holding the dispatchId,
    //     so the shared CFuncPtr can find the Scala dispatch fn without captures.

    private val methodDispatchFns = scala.collection.mutable
        .Map[Int, (GodotObject, Ptr[Ptr[Byte]], Long, Ptr[Byte]) => Unit]()
    private var methodDispatchNextId     = 0
    private var _methodCallFn: Ptr[Byte] = null

    // (classNameBuf, initLevel) in reverse registration order — children-first for unregistration.
    private var registeredClassBufs: List[(Ptr[Byte], Int)] = Nil

    // classNameBufs of auto-activated EditorPlugin subclasses; removed on deinit(Editor).
    private var editorPluginBufs: List[Ptr[Byte]] = Nil

    /** Unregister extension classes from Godot's ClassDB for the given init level.
      *
      * Call from `deinitialize(level)`. Only unregisters classes registered at that level. Resets
      * dispatch state after all classes have been removed (at Scene level).
      */
    def unregisterAll(level: Int = GdxInitLevel.Scene): Unit =
        // Remove editor plugins before unregistering their classes — Godot requires this ordering.
        if level == GdxInitLevel.Editor then
            for buf <- editorPluginBufs do GdxApi.editorRemovePlugin(buf)
            editorPluginBufs = Nil
        end if
        val (toRemove, toKeep) = registeredClassBufs.partition(_._2 == level)
        for (buf, _) <- toRemove do
            // Only unregister classes still present in ClassDB — on hot-reload Godot may have
            // already torn down a class (race between old-image deinit and new-image init).
            if GdxApi.getClassTag(buf) != null then GdxApi.unregisterClass(gdxLibrary, buf)
        end for
        registeredClassBufs = toKeep
        // Reset dispatch state once all classes are gone.
        if registeredClassBufs.isEmpty then
            instanceMap = Map.empty
            godotPtrMap = Map.empty
            instanceToGodotPtr = Map.empty
            instanceClassMap = Map.empty
            factoryMap = Map.empty
            virtualTables = Map.empty
            propertyTables = Map.empty
            freeFns = Map.empty
            editorPluginBufs = Nil
            dispatchFns.clear()
            dispatchNextId = 0
            methodDispatchFns.clear()
            methodDispatchNextId = 0
            _createFn = null
            _recreateFn = null
            _getCallDataFn = null
            _callWithDataFn = null
            _setFn = null
            _getFn = null
            _methodCallFn = null
        end if
    end unregisterAll

    def register(level: Int = GdxInitLevel.Scene): Unit =
        val getProcAddr = gdxGetProcAddress
        val library     = gdxLibrary

        val stringNameNewPtr = getProcAddr(c"string_name_new_with_utf8_chars")
        if stringNameNewPtr == null then return

        val stringNameNew = CFuncPtr.fromPtr[StringNameNewFn](stringNameNewPtr)

        // Shared CFuncPtrs are built once; safe to call again on subsequent levels.
        buildSharedCreateFn()
        buildSharedRecreateFn()
        buildSharedVirtualFns()
        buildSharedPropertyFns()
        buildSharedMethodCallFn()

        for reg <- GdClassRegistry.getRegistrations if reg.initLevel == level do
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
                    instanceToGodotPtr.get(instancePtr)
                        .foreach { godotPtr => godotPtrMap -= godotPtr }
                    instanceToGodotPtr -= instancePtr
                    instanceMap -= instancePtr
                    instanceClassMap -= instancePtr
                    free(instancePtr)
                }
            freeFns += (reg.name -> freeFn)

            buildVirtualTable(reg.virtuals, stringNameNew, userdataPtr)
            val hasProps = reg.properties
                .exists { case PropertyItem.Prop(_) => true; case _ => false }
            val propSNMap = buildPropertyInternTable(reg.properties, stringNameNew, userdataPtr)

            // ── ClassCreationInfo3 (Godot 4.3+): adds is_runtime for hot-reload ──
            // Heap-allocated so Godot can retain the pointer for the class lifetime.
            // Layout (x86_64): 4 × UByte + 4 pad + 19 × Ptr (8 bytes each) = 160 bytes.
            // Fields filled by fixed byte offset — avoids CStruct23 field-accessor issues.
            val infoRaw = malloc(ClassInfo3Size)
            memset(infoRaw, 0, ClassInfo3Size)
            infoRaw(0) = 0.toByte // is_virtual
            infoRaw(1) = 0.toByte // is_abstract
            infoRaw(2) = 1.toByte // is_exposed
            // is_runtime = true for Node subclasses (editor won't tick _ready/_process while editing);
            // false for Resource/Object subclasses (real instances required in editor, not placeholders).
            infoRaw(3) = (if reg.isRuntime then 1 else 0).toByte
            // offset 4–7: implicit struct padding (no write needed, already zeroed)
            setInfoPtr(infoRaw, 8, if hasProps then _setFn else null)
            setInfoPtr(infoRaw, 16, if hasProps then _getFn else null)
            // offsets 24–88: optional callbacks (null = not used, already zeroed)
            setInfoPtr(infoRaw, 96, CFuncPtr.toPtr(_createFn).asInstanceOf[Ptr[Byte]])
            setInfoPtr(infoRaw, 104, CFuncPtr.toPtr(freeFn).asInstanceOf[Ptr[Byte]])
            setInfoPtr(infoRaw, 112, CFuncPtr.toPtr(_recreateFn).asInstanceOf[Ptr[Byte]])
            // offset 120: get_virtual_func (null — using call_data pattern at 128+136 instead)
            setInfoPtr(infoRaw, 128, _getCallDataFn)
            setInfoPtr(infoRaw, 136, _callWithDataFn)
            // offset 144: get_rid_func (null)
            setInfoPtr(infoRaw, 152, userdataPtr)

            GdxApi.registerClass3(library, classNameBuf, parentNameBuf, infoRaw)
            // Prepend so the list ends up in reverse registration order (children first).
            registeredClassBufs = (classNameBuf, reg.initLevel) :: registeredClassBufs

            // Auto-activate EditorPlugin subclasses so the user doesn't need a project.godot entry.
            // Checked against direct parent only — covers 99% of use cases.
            if reg.initLevel == GdxInitLevel.Editor && reg.parentName == "EditorPlugin" then
                GdxApi.editorAddPlugin(classNameBuf)
                editorPluginBufs = classNameBuf :: editorPluginBufs
            end if

            // Register property items in declaration order. Must be called AFTER registerClass3.
            // Group/Subgroup/Category markers produce inspector section headers; Prop items
            // register the actual property (Godot copies PropertyInfo on this call).
            for item <- reg.properties do
                item match
                    case PropertyItem.Prop(desc) => GdxApi
                            .registerProperty(gdxLibrary, classNameBuf, propSNMap(desc.name), desc)
                    case PropertyItem.Group(name, prefix) => GdxApi
                            .registerPropertyGroup(gdxLibrary, classNameBuf, name, prefix)
                    case PropertyItem.Subgroup(name, prefix) => GdxApi
                            .registerPropertySubgroup(gdxLibrary, classNameBuf, name, prefix)
                    case PropertyItem.Category(name) => GdxApi
                            .registerPropertyCategory(gdxLibrary, classNameBuf, name)
            end for

            buildMethodTable(reg.methods, stringNameNew, classNameBuf)
            buildSignalTable(reg.signals, stringNameNew, classNameBuf)
        end for
    end register

    // ── virtual dispatch helpers ────────────────────────────────────────────

    /** Intern each virtual's StringName, store the dispatch fn in the global registry, and
      * pre-allocate a Ptr[Int] callDataBuf per entry. Stored in `virtualTables` keyed by
      * `userdataPtr` so the shared CFuncPtrs can find it without any local captures.
      */
    private def buildVirtualTable(
        virtuals: Vector[VirtualEntry],
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
                factoryMap.get(userdata) match
                    case Some((factory, parentNameBuf, classNameBuf)) =>
                        val godotPtr = GdxApi.constructObject(parentNameBuf)
                        val obj      = factory()
                        obj.ptr = godotPtr
                        val instancePtr        = malloc(1).asInstanceOf[Ptr[Byte]]
                        instanceMap += (instancePtr        -> obj)
                        instanceClassMap += (instancePtr   -> userdata)
                        godotPtrMap += (godotPtr           -> obj)
                        instanceToGodotPtr += (instancePtr -> godotPtr)
                        GdxApi.setInstance(godotPtr, classNameBuf, instancePtr)
                        godotPtr
                    case None => null
                end match
            }
        end if
    end buildSharedCreateFn

    /** Build the shared recreate-instance CFuncPtr (once per extension load).
      *
      * Called by Godot during hot-reload for each scene node that had an extension instance.
      * Creates a fresh Scala object, wires it to the existing Godot engine object, and returns the
      * new instancePtr so Godot can resume virtual dispatch.
      */
    private def buildSharedRecreateFn(): Unit =
        if _recreateFn == null then
            _recreateFn = CFuncPtr2
                .fromScalaFunction[Ptr[Byte], Ptr[Byte], Ptr[Byte]] { (userdata, godotPtr) =>
                    factoryMap.get(userdata) match
                        case Some((factory, _, classNameBuf)) =>
                            val obj = factory()
                            obj.ptr = godotPtr
                            val instancePtr      = malloc(1).asInstanceOf[Ptr[Byte]]
                            instanceMap += (instancePtr      -> obj)
                            instanceClassMap += (instancePtr -> userdata)
                            // Update the godotPtr mapping to the fresh Scala instance.
                            godotPtrMap += (godotPtr           -> obj)
                            instanceToGodotPtr += (instancePtr -> godotPtr)
                            GdxApi.setInstance(godotPtr, classNameBuf, instancePtr)
                            instancePtr
                        case None => null
                    end match
                }
        end if
    end buildSharedRecreateFn

    // ── property dispatch helpers ───────────────────────────────────────────

    /** Build the two shared property CFuncPtrs (once per extension load).
      *
      * Assigned to ClassCreationInfo2 fields 4 and 5 for every class that has exported properties.
      */
    private def buildSharedPropertyFns(): Unit =
        if _setFn == null then
            val fn = CFuncPtr3.fromScalaFunction[Ptr[Byte], Ptr[Byte], Ptr[Byte], UByte] {
                (instancePtr, namePtr, valuePtr) =>
                    val nameData = !(namePtr.asInstanceOf[Ptr[Long]])
                    var found    = false
                    instanceClassMap.get(instancePtr).foreach { userdataPtr =>
                        propertyTables.get(userdataPtr).foreach { table =>
                            instanceMap.get(instancePtr).foreach { obj =>
                                var i = 0
                                while i < table.length && !found do
                                    if table(i)._1 == nameData then
                                        table(i)._2.setter(obj, valuePtr)
                                        found = true
                                    i += 1
                                end while
                            }
                        }
                    }
                    (if found then 1 else 0).toUByte
            }
            _setFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _getFn == null then
            val fn = CFuncPtr3.fromScalaFunction[Ptr[Byte], Ptr[Byte], Ptr[Byte], UByte] {
                (instancePtr, namePtr, retPtr) =>
                    val nameData = !(namePtr.asInstanceOf[Ptr[Long]])
                    var found    = false
                    instanceClassMap.get(instancePtr).foreach { userdataPtr =>
                        propertyTables.get(userdataPtr).foreach { table =>
                            instanceMap.get(instancePtr).foreach { obj =>
                                var i = 0
                                while i < table.length && !found do
                                    if table(i)._1 == nameData then
                                        table(i)._2.getter(obj, retPtr)
                                        found = true
                                    i += 1
                                end while
                            }
                        }
                    }
                    (if found then 1 else 0).toUByte
            }
            _getFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if
    end buildSharedPropertyFns

    /** Intern each Prop item's StringName, build the set/get dispatch table in `propertyTables`,
      * and return a `propName → SN buffer` map so the caller can look up the SN buffer per property
      * when iterating items in order for registration.
      *
      * Only `PropertyItem.Prop` items contribute to the dispatch table; marker items are ignored.
      */
    private def buildPropertyInternTable(
        items: List[PropertyItem],
        stringNameNew: StringNameNewFn,
        userdataPtr: Ptr[Byte]
    ): Map[String, Ptr[Byte]] =
        val propItems = items.collect { case PropertyItem.Prop(d) => d }
        val snMap     = propItems.map { prop =>
            val snBuf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
            memset(snBuf, 0, StringNameSize)
            Zone { stringNameNew(snBuf, toCString(prop.name)) }
            val internData = !(snBuf.asInstanceOf[Ptr[Long]])
            (prop.name, snBuf, internData, prop)
        }
        propertyTables += (userdataPtr -> snMap.map(t => (t._3, t._4)).toArray)
        snMap.map(t => (t._1, t._2)).toMap
    end buildPropertyInternTable

    // ── method dispatch helpers ─────────────────────────────────────────────

    private def buildSharedMethodCallFn(): Unit =
        if _methodCallFn == null then
            val fn = CFuncPtr6
                .fromScalaFunction[Ptr[Byte], Ptr[Byte], Ptr[Ptr[Byte]], Long, Ptr[Byte], Ptr[
                  Byte
                ], Unit] { (methodUserdata, instancePtr, args, argCount, rReturn, _rError) =>
                    val id = !methodUserdata.asInstanceOf[Ptr[Int]]
                    methodDispatchFns.get(id).foreach { dispatch =>
                        instanceMap.get(instancePtr).foreach(dispatch(_, args, argCount, rReturn))
                    }
                }
            _methodCallFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if
    end buildSharedMethodCallFn

    /** Register each method with Godot's ClassDB and store its dispatch fn.
      *
      * StringName buffers are heap-allocated (never freed) because Godot may retain pointers to
      * them after the registration call returns.
      */
    private def buildMethodTable(
        methods: List[MethodEntry],
        stringNameNew: StringNameNewFn,
        classNameBuf: Ptr[Byte]
    ): Unit = for method <- methods do
        val snBuf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
        memset(snBuf, 0, StringNameSize)
        Zone { stringNameNew(snBuf, toCString(method.name)) }

        val id = methodDispatchNextId; methodDispatchNextId += 1
        methodDispatchFns(id) = method.dispatch
        val callDataBuf = malloc(4).asInstanceOf[Ptr[Int]]
        !callDataBuf = id

        GdxApi.registerMethod(
          gdxLibrary,
          classNameBuf,
          snBuf,
          callDataBuf.asInstanceOf[Ptr[Byte]],
          _methodCallFn,
          method.hasReturnValue,
          method.returnVariantType,
          method.argumentCount
        )
    end buildMethodTable

    // ── ClassCreationInfo3 helpers ──────────────────────────────────────────

    // ClassCreationInfo3: 4 × UByte (4 bytes) + 4 pad + 19 × Ptr (8 bytes) = 160 bytes
    private val ClassInfo3Size: CSize = 160.toUSize

    // Write a Ptr[Byte] at a byte offset into the raw struct buffer.
    @inline
    private def setInfoPtr(base: Ptr[Byte], offset: Int, value: Ptr[Byte]): Unit =
        !(base + offset).asInstanceOf[Ptr[Ptr[Byte]]] = value

    // ── signal registration ─────────────────────────────────────────────────

    private def buildSignalTable(
        signals: List[SignalDescriptor],
        stringNameNew: StringNameNewFn,
        classNameBuf: Ptr[Byte]
    ): Unit = for signal <- signals do
        val snBuf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
        memset(snBuf, 0, StringNameSize)
        Zone { stringNameNew(snBuf, toCString(signal.name)) }

        if signal.params.isEmpty then GdxApi.registerSignal(gdxLibrary, classNameBuf, snBuf)
        else
            // Heap-allocate a PropertyInfo array for the signal's typed parameters.
            // Godot retains this pointer for the class lifetime — never freed intentionally.
            val count   = signal.params.size
            val infoArr = malloc(sizeof[PropertyInfo] * count.toUSize)
                .asInstanceOf[Ptr[PropertyInfo]]
            memset(infoArr.asInstanceOf[Ptr[Byte]], 0, sizeof[PropertyInfo] * count.toUSize)

            for (param, i) <- signal.params.zipWithIndex do
                val elem = infoArr + i
                elem._1 = param.variantType.toUInt

                val nameSN = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
                memset(nameSN, 0, StringNameSize)
                Zone { stringNameNew(nameSN, toCString(param.name)) }
                elem._2 = nameSN

                val clsSN = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
                memset(clsSN, 0, StringNameSize)
                if param.className.nonEmpty then
                    Zone { stringNameNew(clsSN, toCString(param.className)) }
                elem._3 = clsSN

                elem._4 = 0.toUInt // hint = None
                val emptyStr = malloc(8).asInstanceOf[Ptr[Byte]]
                memset(emptyStr, 0, 8.toUSize)
                elem._5 = emptyStr                     // hint_string = ""
                elem._6 = PropertyUsage.Default.toUInt // = 6
            end for

            GdxApi.registerSignal(
              gdxLibrary,
              classNameBuf,
              snBuf,
              infoArr.asInstanceOf[Ptr[Byte]],
              count.toLong
            )
        end if

    /** Find the canonical Scala instance for a Godot engine object pointer.
      *
      * Returns the same Scala instance that was created when Godot called `create_instance_func`,
      * so callers get the real live object with all its field state rather than a fresh empty
      * wrapper. Returns `None` for engine objects that are not user-defined extension classes.
      */
    def instanceForGodotPtr(ptr: Ptr[Byte]): Option[GodotObject] = godotPtrMap.get(ptr)

    // ── string helpers ──────────────────────────────────────────────────────

    private def toCString(s: String)(using Zone): CString = scalanative.unsafe.toCString(s)
end ClassRegistrar
