package net.`julian-avar`.gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*
import net.`julian-avar`.gdext.core.method.MethodEntry

/** Bridges a compiled `@gdclass` registration to Godot's script-instance mechanism
  * (`GDExtensionScriptInstanceInfo3` / `script_instance_create3`), so a class can be attached as a
  * Script to an arbitrary generic node (e.g. via the editor's "Attach Script" dialog) and still
  * have its methods/lifecycle virtuals dispatch at runtime — unlike ClassRegistrar, which registers
  * a whole new native ClassDB type instead of binding to an existing engine object.
  *
  * Mirrors ClassRegistrar's patterns: Scala Native forbids CFuncPtr lambdas from closing over
  * local/parameter state, so all dispatch state lives in singleton mutable maps keyed by raw
  * `Ptr[Byte]` tokens.
  */
private[gdext] object ScriptInstanceRegistrar:

    private case class ScriptBinding(
        obj: GodotObject,
        reg: GdClassRegistration,
        scriptPtr: Ptr[Byte],
        languagePtr: Ptr[Byte]
    )

    // instanceDataPtr (our own opaque token, passed to script_instance_create3) → binding.
    private var bindings: Map[Ptr[Byte], ScriptBinding] = Map.empty

    // forObjectPtr (the engine object the script is attached to) → live Scala instance.
    // Lets GdClassRegistry.lookupByPtr resolve to the canonical instance for script-attached
    // nodes, the same way ClassRegistrar.instanceForGodotPtr does for native ClassDB instances.
    private var godotPtrMap: Map[Ptr[Byte], GodotObject] = Map.empty

    // reg.name → Array[(internedNameToken, MethodEntry)], built once per class and reused across
    // every instance of that class. internedNameToken is the 8-byte payload of a StringName
    // buffer (StringName instances referring to the same text share this payload — see
    // ClassRegistrar's identical technique for virtual/method/property dispatch tables).
    private var methodTables: Map[String, Array[(Long, MethodEntry)]] = Map.empty

    private def methodTableFor(reg: GdClassRegistration): Array[(Long, MethodEntry)] = methodTables
        .getOrElse(
          reg.name, {
              val table = reg.scriptCallMethods.map { entry =>
                  val snBuf = StringNames.cached(entry.name)
                  (!(snBuf.asInstanceOf[Ptr[Long]]), entry)
              }.toArray
              methodTables += (reg.name -> table)
              table
          }
        )

    // ── raw interface functions (resolved lazily, mirrors GdxApi's own getProcAddr pattern) ──

    private type ScriptInstanceCreate3Fn = CFuncPtr2[Ptr[Byte], Ptr[Byte], Ptr[Byte]]
    private type PlaceholderCreateFn     = CFuncPtr3[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte]]

    private var _createInstanceFn: ScriptInstanceCreate3Fn = null
    private var _placeholderFn: PlaceholderCreateFn        = null

    private def ensureCreateInstanceFn(): ScriptInstanceCreate3Fn =
        if _createInstanceFn == null then
            val addr = gdxGetProcAddress(c"script_instance_create3")
            if addr != null then _createInstanceFn = CFuncPtr.fromPtr[ScriptInstanceCreate3Fn](addr)
        end if
        _createInstanceFn
    end ensureCreateInstanceFn

    private def ensurePlaceholderFn(): PlaceholderCreateFn =
        if _placeholderFn == null then
            val addr = gdxGetProcAddress(c"placeholder_script_instance_create")
            if addr != null then _placeholderFn = CFuncPtr.fromPtr[PlaceholderCreateFn](addr)
        end if
        _placeholderFn
    end ensurePlaceholderFn

    // ── the shared GDExtensionScriptInstanceInfo3 vtable ────────────────────
    // 26 × 8-byte function-pointer fields, no leading scalar fields (unlike ClassCreationInfo3) —
    // built once and reused for every script instance across every class, since none of the 26
    // fields close over per-instance state (all per-instance lookups go through `bindings`,
    // keyed by the `p_instance_data` token Godot passes back on every call).
    private val InfoSize: CSize                = 208.toUSize
    private var _infoBuf: Ptr[Byte]            = null
    private var _setFn: Ptr[Byte]              = null
    private var _getFn: Ptr[Byte]              = null
    private var _getPropertyListFn: Ptr[Byte]  = null
    private var _freePropertyListFn: Ptr[Byte] = null
    private var _getPropertyTypeFn: Ptr[Byte]  = null
    private var _getMethodListFn: Ptr[Byte]    = null
    private var _freeMethodListFn: Ptr[Byte]   = null
    private var _hasMethodFn: Ptr[Byte]        = null
    private var _callFn: Ptr[Byte]             = null
    private var _getScriptFn: Ptr[Byte]        = null
    private var _getLanguageFn: Ptr[Byte]      = null
    private var _freeFn: Ptr[Byte]             = null

    @inline
    private def setInfoPtr(base: Ptr[Byte], offset: Int, value: Ptr[Byte]): Unit =
        !(base + offset).asInstanceOf[Ptr[Ptr[Byte]]] = value

    /** Write a GDExtensionCallError's `error` field (offset 0 of the 12-byte {error, argument,
      * expected} struct). `0` = OK, `1` = CALL_ERROR_INVALID_METHOD.
      */
    @inline
    private def writeCallError(rError: Ptr[Byte], code: Int): Unit =
        if rError != null then !(rError.asInstanceOf[Ptr[CUnsignedInt]]) = code.toUInt

    private def buildSharedFns(): Unit =
        if _setFn == null then
            val fn = CFuncPtr3
                .fromScalaFunction[Ptr[Byte], Ptr[Byte], Ptr[Byte], UByte] { (_, _, _) =>
                    0.toUByte // MVP: no @gdexport property dispatch for script instances yet
                }
            _setFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _getFn == null then
            val fn = CFuncPtr3.fromScalaFunction[Ptr[Byte], Ptr[Byte], Ptr[Byte], UByte] {
                (_, _, _) => 0.toUByte
            }
            _getFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _getPropertyListFn == null then
            val fn = CFuncPtr2.fromScalaFunction[Ptr[Byte], Ptr[UInt], Ptr[Byte]] { (_, rCount) =>
                if rCount != null then !rCount = 0.toUInt
                null
            }
            _getPropertyListFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _freePropertyListFn == null then
            val fn = CFuncPtr3.fromScalaFunction[Ptr[Byte], Ptr[Byte], UInt, Unit] { (_, _, _) =>
                ()
            }
            _freePropertyListFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _getPropertyTypeFn == null then
            val fn = CFuncPtr3
                .fromScalaFunction[Ptr[Byte], Ptr[Byte], Ptr[UByte], UInt] { (_, _, rIsValid) =>
                    if rIsValid != null then !rIsValid = 0.toUByte
                    0.toUInt // NIL
                }
            _getPropertyTypeFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _getMethodListFn == null then
            val fn = CFuncPtr2.fromScalaFunction[Ptr[Byte], Ptr[UInt], Ptr[Byte]] { (_, rCount) =>
                if rCount != null then !rCount = 0.toUInt
                null
            }
            _getMethodListFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _freeMethodListFn == null then
            val fn = CFuncPtr3.fromScalaFunction[Ptr[Byte], Ptr[Byte], UInt, Unit] { (_, _, _) =>
                ()
            }
            _freeMethodListFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _hasMethodFn == null then
            val fn = CFuncPtr2
                .fromScalaFunction[Ptr[Byte], Ptr[Byte], UByte] { (dataPtr, namePtr) =>
                    val nameData = !(namePtr.asInstanceOf[Ptr[Long]])
                    val found    = bindings.get(dataPtr)
                        .exists { binding => methodTableFor(binding.reg).exists(_._1 == nameData) }
                    (if found then 1 else 0).toUByte
                }
            _hasMethodFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _callFn == null then
            val fn = CFuncPtr6
                .fromScalaFunction[Ptr[Byte], Ptr[Byte], Ptr[Ptr[Byte]], Long, Ptr[Byte], Ptr[
                  Byte
                ], Unit] { (dataPtr, namePtr, argsPtr, argCount, rReturn, rError) =>
                    val nameData = !(namePtr.asInstanceOf[Ptr[Long]])
                    bindings.get(dataPtr) match
                        case Some(binding) =>
                            methodTableFor(binding.reg).find(_._1 == nameData) match
                                case Some((_, entry)) =>
                                    entry.dispatch(binding.obj, argsPtr, argCount, rReturn)
                                    writeCallError(rError, 0)
                                case None => writeCallError(rError, 1)
                        case None => writeCallError(rError, 1)
                    end match
                }
            _callFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _getScriptFn == null then
            val fn = CFuncPtr1.fromScalaFunction[Ptr[Byte], Ptr[Byte]] { dataPtr =>
                bindings.get(dataPtr).map(_.scriptPtr).orNull
            }
            _getScriptFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _getLanguageFn == null then
            val fn = CFuncPtr1.fromScalaFunction[Ptr[Byte], Ptr[Byte]] { dataPtr =>
                bindings.get(dataPtr).map(_.languagePtr).orNull
            }
            _getLanguageFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if

        if _freeFn == null then
            val fn = CFuncPtr1.fromScalaFunction[Ptr[Byte], Unit] { dataPtr =>
                bindings.get(dataPtr).foreach { binding =>
                    val forPtr = binding.obj.ptr
                    if forPtr != null then godotPtrMap -= forPtr
                }
                bindings -= dataPtr
                free(dataPtr)
            }
            _freeFn = CFuncPtr.toPtr(fn).asInstanceOf[Ptr[Byte]]
        end if
    end buildSharedFns

    private def ensureInfoBuf(): Ptr[Byte] =
        if _infoBuf == null then
            buildSharedFns()
            val buf = malloc(InfoSize)
            memset(buf, 0, InfoSize)
            setInfoPtr(buf, 0, _setFn)
            setInfoPtr(buf, 8, _getFn)
            setInfoPtr(buf, 16, _getPropertyListFn)
            setInfoPtr(buf, 24, _freePropertyListFn)
            // offset 32: get_class_category_func — null = default behavior
            // offsets 40–64: property_can_revert/get_revert, get_owner, get_property_state — null
            setInfoPtr(buf, 72, _getMethodListFn)
            setInfoPtr(buf, 80, _freeMethodListFn)
            setInfoPtr(buf, 88, _getPropertyTypeFn)
            // offset 96: validate_property_func — null
            setInfoPtr(buf, 104, _hasMethodFn)
            // offset 112: get_method_argument_count_func — null
            setInfoPtr(buf, 120, _callFn)
            // offset 128: notification_func — null (not required for _ready/_process; those
            // dispatch through call_func, per Godot's GDVIRTUAL_CALL script-instance check)
            // offsets 136–152: to_string, refcount_incremented/decremented — null
            setInfoPtr(buf, 160, _getScriptFn)
            // offset 168: is_placeholder_func — null (defaults false, correct for real instances)
            // offsets 176–184: set_fallback/get_fallback — null
            setInfoPtr(buf, 192, _getLanguageFn)
            setInfoPtr(buf, 200, _freeFn)
            _infoBuf = buf
        end if
        _infoBuf
    end ensureInfoBuf

    /** Bind a fresh instance of `reg`'s Scala class to an existing engine object (`forObjectPtr`)
      * and register it with Godot's script-instance machinery.
      *
      * Unlike ClassRegistrar's create-instance path (which constructs a brand-new engine object via
      * `GdxApi.constructObject`), this binds to an object Godot already created — the whole point
      * of the "attach script to a generic node" workflow. Returns the opaque
      * `GDExtensionScriptInstancePtr` that must be returned verbatim from
      * `ScriptExtension._instance_create`.
      */
    def createInstance(
        reg: GdClassRegistration,
        forObjectPtr: Ptr[Byte],
        scriptPtr: Ptr[Byte],
        languagePtr: Ptr[Byte]
    ): Ptr[Byte] =
        val createFn = ensureCreateInstanceFn()
        if createFn == null then null
        else
            val infoBuf = ensureInfoBuf()
            val obj     = reg.factory()
            obj.ptr = forObjectPtr
            val dataPtr         = malloc(1).asInstanceOf[Ptr[Byte]]
            bindings += (dataPtr         -> ScriptBinding(obj, reg, scriptPtr, languagePtr))
            godotPtrMap += (forObjectPtr -> obj)
            createFn(infoBuf, dataPtr)
        end if
    end createInstance

    /** Fall back to Godot's built-in placeholder script instance — used when the attached script's
      * declared class isn't (yet) a registered `@gdclass` (e.g. written but not rebuilt), so the
      * editor degrades gracefully instead of crashing.
      */
    def createPlaceholder(
        languagePtr: Ptr[Byte],
        scriptPtr: Ptr[Byte],
        forObjectPtr: Ptr[Byte]
    ): Ptr[Byte] =
        val fn = ensurePlaceholderFn()
        if fn == null then null else fn(languagePtr, scriptPtr, forObjectPtr)
    end createPlaceholder

    /** Find the canonical Scala instance for a Godot engine object pointer whose behavior was bound
      * via a script instance (as opposed to native ClassDB registration — see
      * `ClassRegistrar.instanceForGodotPtr`, checked first by `GdClassRegistry.lookupByPtr`).
      */
    def instanceForGodotPtr(ptr: Ptr[Byte]): Option[GodotObject] = godotPtrMap.get(ptr)

    /** Fallback — scan live script bindings for one whose `.obj.ptr` matches `ptr`.
      *
      * Mirrors `ClassRegistrar.findInstanceByPtrFallback` for the same reason: the `forObjectPtr`
      * stored in `godotPtrMap` may differ from the pointer Godot passes as a virtual-call argument,
      * so a direct map lookup can miss.
      */
    def findInstanceByPtrFallback(ptr: Ptr[Byte]): Option[GodotObject] = bindings.values.map(_.obj)
        .find(_.ptr == ptr)
end ScriptInstanceRegistrar
