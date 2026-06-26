package gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*
// import gdext.generated.UtilityFunctions

type MethodBindPtr = Ptr[Byte]
// classdb_get_method_bind(p_classname, p_methodname, p_hash)
type GetMethodBindFn = CFuncPtr3[Ptr[Byte], Ptr[Byte], Long, MethodBindPtr]
// object_method_bind_ptrcall: raw type pointer convention (no argCount, no error out)
type PtrcallFn         = CFuncPtr4[MethodBindPtr, Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], Unit]
type ConstructObjectFn = CFuncPtr1[CString, Ptr[Byte]]
// callable_custom_create2 call_func signature: (userdata, args, argCount, ret, error) → Unit
type CallableCallFn = CFuncPtr5[Ptr[Byte], Ptr[Ptr[Byte]], Long, Ptr[Byte], Ptr[Byte], Unit]

private type GetUtilityFnPtrFn = CFuncPtr2[Ptr[Byte], Long, Ptr[Byte]]
private type UtilityCallFn     = CFuncPtr3[Ptr[Byte], Ptr[Ptr[Byte]], CInt, Unit]
private type StringNewFn       = CFuncPtr2[Ptr[Byte], CString, Unit]
// string_to_utf8_chars(p_self, r_buffer, p_max_write_count) → void
private type StringToUtf8Fn     = CFuncPtr3[Ptr[Byte], Ptr[Byte], Long, Long]
private type GetVFTCtorFn       = CFuncPtr1[CUnsignedInt, Ptr[Byte]]
private type VFTCtorFn          = CFuncPtr2[Ptr[Byte], Ptr[Byte], Unit]
private type GetPtrDestructorFn = CFuncPtr1[CUnsignedInt, Ptr[Byte]]
private type PtrDestructorFn    = CFuncPtr1[Ptr[Byte], Unit]
// variant_get_ptr_constructor(type, index) → constructor fn
private type GetPtrCtorFn = CFuncPtr2[CUnsignedInt, CInt, Ptr[Byte]]
// ptr constructor fn: (dest, args) → Unit  (args = null for default ctor)
private type PtrTypeCtorFn = CFuncPtr2[Ptr[Byte], Ptr[Ptr[Byte]], Unit]
// variant_get_ptr_builtin_method(type, name, hash) → method fn
private type GetPtrBuiltinMethodFn = CFuncPtr3[CUnsignedInt, Ptr[Byte], Long, Ptr[Byte]]
// builtin method fn: (base, args, ret, arg_count) → Unit
private type BuiltinMethodFn = CFuncPtr4[Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], CInt, Unit]
// NodePath ptr constructor: (dest, args) → Unit
private type NodePathCtorFn   = CFuncPtr2[Ptr[Byte], Ptr[Ptr[Byte]], Unit]
private type VariantDestroyFn = CFuncPtr1[Ptr[Byte], Unit]
// p_o, p_classname (StringName*), p_instance
private type ObjectSetInstanceFn = CFuncPtr3[Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]
// callable_custom_create2(r_callable, p_info)
private type CallableCreateFn = CFuncPtr2[Ptr[Byte], Ptr[Byte], Unit]
private type GetSingletonFn         = CFuncPtr1[Ptr[Byte], Ptr[Byte]]
// classdb_unregister_extension_class(library, classNameSN)
private type UnregisterClassFn   = CFuncPtr2[Ptr[Byte], Ptr[Byte], Unit]
// classdb_register_extension_class_signal(library, class_name, signal_name, arg_info, arg_count)
private type RegisterSignalFn    = CFuncPtr5[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte], Long, Unit]
// object_method_bind_call(method_bind, instance, args: Ptr[Variant*], arg_count, ret_variant, error)
private type MethodBindCallFn    = CFuncPtr6[Ptr[Byte], Ptr[Byte], Ptr[Ptr[Byte]], Long, Ptr[Byte], Ptr[Byte], Unit]
private type ObjectCastToFn        = CFuncPtr2[Ptr[Byte], Ptr[Byte], Ptr[Byte]]
private type ObjectGetInstanceIdFn = CFuncPtr1[Ptr[Byte], Long]
private type ObjectDestroyFn       = CFuncPtr1[Ptr[Byte], Unit]

/** Stable registry mapping Int IDs to Scala closures, dispatched via a single static trampoline.
  *
  * Closures are kept alive by the map (GC root) and the Int ID is stored in a malloc'd 4-byte
  * buffer passed as callable userdata to Godot. Because Scala Native's immix GC is non-moving,
  * references inside closures (including `this`) remain valid indefinitely.
  *
  * Note: registered callbacks are never removed — disconnect support is future work.
  */
object CallbackRegistry:
    private val callbacks = scala.collection.mutable.Map[Int, () => Unit]()
    private var nextId    = 0
    private[gdext] def register(cb: () => Unit): Int =
        val id = nextId; nextId += 1; callbacks(id) = cb; id

    /** Single static trampoline — reads callback ID from userdata, dispatches to the closure. */
    val trampoline: CallableCallFn = CFuncPtr5
        .fromScalaFunction[Ptr[Byte], Ptr[Ptr[Byte]], Long, Ptr[Byte], Ptr[Byte], Unit] {
            (userdata, _, _, _, rError) =>
                if rError != null then !rError.asInstanceOf[Ptr[Int]] = 0 // GDEXTENSION_CALL_OK
                val id = !userdata.asInstanceOf[Ptr[Int]]
                callbacks.get(id).foreach(_.apply())
        }
end CallbackRegistry

object GdxApi:
    private var getMethodBindPtr: GetMethodBindFn        = scala.compiletime.uninitialized
    private[gdext] var ptrcallPtr: PtrcallFn             = scala.compiletime.uninitialized
    private var constructObjectPtr: ConstructObjectFn    = scala.compiletime.uninitialized
    private var objectSetInstanceFn: ObjectSetInstanceFn = scala.compiletime.uninitialized
    private var callableCreatePtr: CallableCreateFn      = scala.compiletime.uninitialized
    private[gdext] var connectMethodBind: Ptr[Byte]      = null
    private var getUtilFnPtr: GetUtilityFnPtrFn          = scala.compiletime.uninitialized
    private var stringNameNewPtr: StringNameNewFn        = scala.compiletime.uninitialized
    private var stringNewFn: StringNewFn                 = scala.compiletime.uninitialized
    private var strToUtf8Fn: Ptr[Byte]                   = null
    private var variantFromStrCtor: Ptr[Byte]            = null
    private var variantToStrCtor: Ptr[Byte]              = null
    private var strDestructorPtr: Ptr[Byte]              = null
    private var nodepathFromStrCtorPtr: Ptr[Byte]        = null
    private var nodepathDestructorPtr: Ptr[Byte]         = null
    private var variantDestroyPtr: Ptr[Byte]             = null
    private var cachedPrintFn: Ptr[Byte]                 = null
    private var globalGetSingletonFn: GetSingletonFn     = scala.compiletime.uninitialized
    private var registerPropertyFnAddr: Ptr[Byte]        = null
    private var registerMethodFnAddr: Ptr[Byte]          = null
    private var unregisterClassFnPtr: UnregisterClassFn  = scala.compiletime.uninitialized
    private var pktStrArrayCtorPtr: Ptr[Byte]            = null
    private var pktStrArrayPushBackPtr: Ptr[Byte]        = null
    // Array (TYPE_ARRAY = 28) builtin method pointers
    private[gdext] var arrayDefaultCtorPtr: Ptr[Byte]   = null
    private[gdext] var arraySizeFnPtr: Ptr[Byte]        = null
    private[gdext] var arrayGetFnPtr: Ptr[Byte]         = null
    private[gdext] var arraySetFnPtr: Ptr[Byte]         = null
    private[gdext] var arrayPushBackFnPtr: Ptr[Byte]    = null
    private[gdext] var arrayClearFnPtr: Ptr[Byte]       = null
    // Dictionary (TYPE_DICTIONARY = 27) builtin method pointers
    private[gdext] var dictDefaultCtorPtr: Ptr[Byte]    = null
    private[gdext] var dictSizeFnPtr: Ptr[Byte]         = null
    private[gdext] var dictHasFnPtr: Ptr[Byte]          = null
    private[gdext] var dictGetFnPtr: Ptr[Byte]          = null
    private[gdext] var dictSetFnPtr: Ptr[Byte]          = null
    private[gdext] var dictEraseFnPtr: Ptr[Byte]        = null
    private[gdext] var dictClearFnPtr: Ptr[Byte]        = null
    private var registerSignalFnPtr: RegisterSignalFn    = scala.compiletime.uninitialized
    private var methodBindCallPtr: MethodBindCallFn      = scala.compiletime.uninitialized
    private[gdext] var emitSignalMethodBind: Ptr[Byte]   = null
    private var variantFromSnCtor: Ptr[Byte]             = null
    private var registerPropertyGroupFnAddr: Ptr[Byte]    = null
    private var registerPropertySubgroupFnAddr: Ptr[Byte] = null
    private var registerClass3FnPtr: RegisterClass3Fn        = scala.compiletime.uninitialized
    private var getClassTagFnPtr: GetClassTagFn               = scala.compiletime.uninitialized
    private var objectCastToFnPtr: ObjectCastToFn             = scala.compiletime.uninitialized
    private var objectGetInstanceIdFnPtr: ObjectGetInstanceIdFn = scala.compiletime.uninitialized
    private var objectDestroyFnPtr: ObjectDestroyFn           = scala.compiletime.uninitialized
    private[gdext] var initRefMethodBind: Ptr[Byte]           = null
    private[gdext] var unreferenceMethodBind: Ptr[Byte]       = null

    private[gdext] def initialize(getProcAddr: GetProcAddressFn): Unit =
        val globalGetSingletonAddr = getProcAddr(c"global_get_singleton")
        if globalGetSingletonAddr != null then
            globalGetSingletonFn = CFuncPtr.fromPtr[GetSingletonFn](globalGetSingletonAddr)

        val getMethodBindAddr     = getProcAddr(c"classdb_get_method_bind")
        val ptrcallAddr           = getProcAddr(c"object_method_bind_ptrcall")
        val constructObjectAddr   = getProcAddr(c"classdb_construct_object")
        val objectSetInstanceAddr = getProcAddr(c"object_set_instance")
        val callableCreateAddr    = getProcAddr(c"callable_custom_create2")
        val getUtilFnAddr         = getProcAddr(c"variant_get_ptr_utility_function")
        val snNewAddr             = getProcAddr(c"string_name_new_with_utf8_chars")
        val strNewAddr            = getProcAddr(c"string_new_with_utf8_chars")
        val vftCtorGetAddr        = getProcAddr(c"get_variant_from_type_constructor")
        val vtfCtorGetAddr        = getProcAddr(c"get_variant_to_type_constructor")
        val ptrDtorGetAddr        = getProcAddr(c"variant_get_ptr_destructor")
        val vDestroyAddr          = getProcAddr(c"variant_destroy")

        if getMethodBindAddr != null then
            getMethodBindPtr = CFuncPtr.fromPtr[GetMethodBindFn](getMethodBindAddr)
        if ptrcallAddr != null then ptrcallPtr = CFuncPtr.fromPtr[PtrcallFn](ptrcallAddr)
        if constructObjectAddr != null then
            constructObjectPtr = CFuncPtr.fromPtr[ConstructObjectFn](constructObjectAddr)
        if objectSetInstanceAddr != null then
            objectSetInstanceFn = CFuncPtr.fromPtr[ObjectSetInstanceFn](objectSetInstanceAddr)
        if callableCreateAddr != null then
            callableCreatePtr = CFuncPtr.fromPtr[CallableCreateFn](callableCreateAddr)
        if getUtilFnAddr != null then
            getUtilFnPtr = CFuncPtr.fromPtr[GetUtilityFnPtrFn](getUtilFnAddr)
        if snNewAddr != null then stringNameNewPtr = CFuncPtr.fromPtr[StringNameNewFn](snNewAddr)
        if strNewAddr != null then stringNewFn = CFuncPtr.fromPtr[StringNewFn](strNewAddr)
        strToUtf8Fn = getProcAddr(c"string_to_utf8_chars")
        if vftCtorGetAddr != null then
            val vftCtor: GetVFTCtorFn = CFuncPtr.fromPtr[GetVFTCtorFn](vftCtorGetAddr)
            variantFromStrCtor = vftCtor(4.toUInt)
        if vtfCtorGetAddr != null then
            val vtfCtor: GetVFTCtorFn = CFuncPtr.fromPtr[GetVFTCtorFn](vtfCtorGetAddr)
            variantToStrCtor = vtfCtor(4.toUInt)
        if ptrDtorGetAddr != null then
            val ptrDtor: GetPtrDestructorFn = CFuncPtr.fromPtr[GetPtrDestructorFn](ptrDtorGetAddr)
            strDestructorPtr = ptrDtor(4.toUInt)       // String destructor
            nodepathDestructorPtr = ptrDtor(22.toUInt) // NodePath destructor
        end if
        if vDestroyAddr != null then variantDestroyPtr = vDestroyAddr

        val ptrCtorGetAddr = getProcAddr(c"variant_get_ptr_constructor")
        if ptrCtorGetAddr != null then
            val ptrCtorGet = CFuncPtr.fromPtr[GetPtrCtorFn](ptrCtorGetAddr)
            nodepathFromStrCtorPtr = ptrCtorGet(22.toUInt, 2) // NodePath(from: String), index 2
            pktStrArrayCtorPtr     = ptrCtorGet(34.toUInt, 0) // PackedStringArray() default ctor
            arrayDefaultCtorPtr    = ptrCtorGet(28.toUInt, 0) // Array() default ctor
            dictDefaultCtorPtr     = ptrCtorGet(27.toUInt, 0) // Dictionary() default ctor
        end if

        val ptrBuiltinMethodAddr = getProcAddr(c"variant_get_ptr_builtin_method")
        if ptrBuiltinMethodAddr != null && snNewAddr != null then
            val getBM = CFuncPtr.fromPtr[GetPtrBuiltinMethodFn](ptrBuiltinMethodAddr)
            // Reusable StringName buffer — written fresh for each lookup (stack-allocated).
            val snBuf = stackalloc[Byte](StringNameSize)

            // PackedStringArray methods
            memset(snBuf, 0, StringNameSize)
            stringNameNewPtr(snBuf, c"push_back")
            pktStrArrayPushBackPtr = getBM(34.toUInt, snBuf, 816187996L)

            // Array (type 28) methods — hashes from Godot 4.x extension_api.json
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"size")
            arraySizeFnPtr     = getBM(28.toUInt, snBuf, 3173160232L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"get")
            arrayGetFnPtr      = getBM(28.toUInt, snBuf, 708700221L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"set")
            arraySetFnPtr      = getBM(28.toUInt, snBuf, 3798478031L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"push_back")
            arrayPushBackFnPtr = getBM(28.toUInt, snBuf, 3316032543L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"clear")
            arrayClearFnPtr    = getBM(28.toUInt, snBuf, 3218959716L)

            // Dictionary (type 27) methods
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"size")
            dictSizeFnPtr  = getBM(27.toUInt, snBuf, 3173160232L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"has")
            dictHasFnPtr   = getBM(27.toUInt, snBuf, 3680194679L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"get")
            dictGetFnPtr   = getBM(27.toUInt, snBuf, 2205440559L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"set")
            dictSetFnPtr   = getBM(27.toUInt, snBuf, 2175348267L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"erase")
            dictEraseFnPtr = getBM(27.toUInt, snBuf, 1776646889L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"clear")
            dictClearFnPtr = getBM(27.toUInt, snBuf, 3218959716L)
        end if

        registerPropertyFnAddr        = getProcAddr(c"classdb_register_extension_class_property")
        registerPropertyGroupFnAddr   = getProcAddr(c"classdb_register_extension_class_property_group")
        registerPropertySubgroupFnAddr = getProcAddr(c"classdb_register_extension_class_property_subgroup")
        registerMethodFnAddr = getProcAddr(c"classdb_register_extension_class_method")
        val unregisterClassAddr = getProcAddr(c"classdb_unregister_extension_class")
        if unregisterClassAddr != null then
            unregisterClassFnPtr = CFuncPtr.fromPtr[UnregisterClassFn](unregisterClassAddr)

        // Cache the print utility function pointer (needs both functions loaded)
        if getUtilFnAddr != null && snNewAddr != null then
            val snBuf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
            memset(snBuf, 0, StringNameSize)
            stringNameNewPtr(snBuf, c"print")
            cachedPrintFn = getUtilFnPtr(snBuf, 2648703342L)
            free(snBuf)
        end if

        // Cache Object::connect and Object::emit_signal method binds
        if getMethodBindAddr != null && snNewAddr != null then
            val objSN    = stackalloc[Byte](StringNameSize)
            val connSN   = stackalloc[Byte](StringNameSize)
            val emitSN   = stackalloc[Byte](StringNameSize)
            memset(objSN, 0, StringNameSize)
            memset(connSN, 0, StringNameSize)
            memset(emitSN, 0, StringNameSize)
            stringNameNewPtr(objSN, c"Object")
            stringNameNewPtr(connSN, c"connect")
            stringNameNewPtr(emitSN, c"emit_signal")
            connectMethodBind    = getMethodBindPtr(objSN, connSN, 1518946055L)
            emitSignalMethodBind = getMethodBindPtr(objSN, emitSN, 4047867050L)
        end if

        val objectCastToAddr = getProcAddr(c"object_cast_to")
        if objectCastToAddr != null then
            objectCastToFnPtr = CFuncPtr.fromPtr[ObjectCastToFn](objectCastToAddr)

        val objectGetInstanceIdAddr = getProcAddr(c"object_get_instance_id")
        if objectGetInstanceIdAddr != null then
            objectGetInstanceIdFnPtr = CFuncPtr.fromPtr[ObjectGetInstanceIdFn](objectGetInstanceIdAddr)

        val objectDestroyAddr = getProcAddr(c"object_destroy")
        if objectDestroyAddr != null then
            objectDestroyFnPtr = CFuncPtr.fromPtr[ObjectDestroyFn](objectDestroyAddr)

        // Cache RefCounted::init_ref and unreference method binds for Gd[T] lifecycle
        if getMethodBindAddr != null && snNewAddr != null then
            val rcSN  = stackalloc[Byte](StringNameSize)
            val irSN  = stackalloc[Byte](StringNameSize)
            val unrSN = stackalloc[Byte](StringNameSize)
            memset(rcSN, 0, StringNameSize)
            memset(irSN, 0, StringNameSize)
            memset(unrSN, 0, StringNameSize)
            stringNameNewPtr(rcSN, c"RefCounted")
            stringNameNewPtr(irSN, c"init_ref")
            stringNameNewPtr(unrSN, c"unreference")
            initRefMethodBind     = getMethodBindPtr(rcSN, irSN, 2240911060L)
            unreferenceMethodBind = getMethodBindPtr(rcSN, unrSN, 2240911060L)

        val registerClass3Addr = getProcAddr(c"classdb_register_extension_class3")
        if registerClass3Addr != null then
            registerClass3FnPtr = CFuncPtr.fromPtr[RegisterClass3Fn](registerClass3Addr)

        val getClassTagAddr = getProcAddr(c"classdb_get_class_tag")
        if getClassTagAddr != null then
            getClassTagFnPtr = CFuncPtr.fromPtr[GetClassTagFn](getClassTagAddr)

        val registerSignalAddr = getProcAddr(c"classdb_register_extension_class_signal")
        if registerSignalAddr != null then
            registerSignalFnPtr = CFuncPtr.fromPtr[RegisterSignalFn](registerSignalAddr)

        val methodBindCallAddr = getProcAddr(c"object_method_bind_call")
        if methodBindCallAddr != null then
            methodBindCallPtr = CFuncPtr.fromPtr[MethodBindCallFn](methodBindCallAddr)

        // Cache from-type constructor for StringName Variant (type 21) — needed for emitSignal
        if vftCtorGetAddr != null then
            val vftCtor = CFuncPtr.fromPtr[GetVFTCtorFn](vftCtorGetAddr)
            variantFromSnCtor = vftCtor(21.toUInt)
    end initialize

    def getMethodBind(className: CString, methodName: CString, hash: Long): MethodBindPtr =
        val cn = StringNames.cached(fromCString(className))
        val mn = StringNames.cached(fromCString(methodName))
        getMethodBindPtr(cn, mn, hash)
    end getMethodBind

    def ptrcall(
        methodBind: MethodBindPtr,
        instance: Ptr[Byte],
        args: Ptr[Ptr[Byte]],
        ret: Ptr[Byte]
    ): Unit = ptrcallPtr(methodBind, instance, args, ret)

    def constructObject(className: CString): Ptr[Byte] = constructObjectPtr(className)

    /** Registers a binding between a Godot Object and an extension class instance. `classNameSN`
      * must be a StringName buffer for the registered extension class name. `instancePtr` is the
      * opaque pointer Godot will pass back to virtual call functions.
      */
    def setInstance(godotPtr: Ptr[Byte], classNameSN: Ptr[Byte], instancePtr: Ptr[Byte]): Unit =
        objectSetInstanceFn(godotPtr, classNameSN, instancePtr)

    /** Connects a Godot signal to a Scala function via `callable_custom_create2`.
      *
      * `callFn` must remain alive (held by a field) for the lifetime of the connection. `userdata`
      * is passed as the first argument to `callFn` on every invocation.
      * GDExtensionCallableCustomInfo2 layout (11 × 8-byte fields, zero except token/call_func):
      * offset 0: callable_userdata offset 8: token (gdxLibrary) offset 16: object_id offset 24:
      * call_func offsets 32–80: optional callbacks (null = unused)
      */
    def connectSignal(
        obj: Ptr[Byte],
        signalCStr: CString,
        callFn: CallableCallFn,
        userdata: Ptr[Byte] = null
    ): Unit =
        val snBuf = stackalloc[Byte](StringNameSize)
        memset(snBuf, 0, StringNameSize)
        stringNameNewPtr(snBuf, signalCStr)

        val info = stackalloc[Byte](88.toUSize)
        memset(info, 0, 88.toUSize)
        val infoPtrs = info.asInstanceOf[Ptr[Ptr[Byte]]]
        infoPtrs(0) = userdata
        infoPtrs(1) = gdxLibrary
        infoPtrs(3) = CFuncPtr.toPtr(callFn).asInstanceOf[Ptr[Byte]]

        val callableBuf = stackalloc[Byte](16)
        memset(callableBuf, 0, 16.toUSize)
        callableCreatePtr(callableBuf, info)

        val flagsBuf = stackalloc[Int]()
        !flagsBuf = 0
        val args = stackalloc[Ptr[Byte]](3)
        args(0) = snBuf
        args(1) = callableBuf
        args(2) = flagsBuf.asInstanceOf[Ptr[Byte]]
        val retBuf = stackalloc[Long]()
        ptrcallPtr(connectMethodBind, obj, args, retBuf.asInstanceOf[Ptr[Byte]])
    end connectSignal

    /** Look up a Godot utility function pointer by name and hash. Heap-allocates a StringName
      * temporarily; the returned Ptr[Byte] is stable and can be cached in a Binds object.
      */
    def getUtilityFunctionPtr(name: CString, hash: Long): Ptr[Byte] =
        val snBuf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
        memset(snBuf, 0, StringNameSize)
        stringNameNewPtr(snBuf, name)
        val fn = getUtilFnPtr(snBuf, hash)
        free(snBuf)
        fn
    end getUtilityFunctionPtr

    /** Call a cached utility function pointer with (ret, args, argCount) convention. */
    def callUtilityFunction(
        fn: Ptr[Byte],
        args: Ptr[Ptr[Byte]],
        argCount: Int,
        ret: Ptr[Byte]
    ): Unit =
        val callFn = CFuncPtr.fromPtr[UtilityCallFn](fn)
        callFn.apply(ret, args, argCount)
    end callUtilityFunction

    /** Initialize a Godot String in a caller-provided 8-byte buffer from a CString. */
    def initGodotString(buf: Ptr[Byte], s: CString): Unit = stringNewFn(buf, s)

    /** Decode a Godot String handle (8-byte buffer) into a Scala String.
      *
      * Uses the two-call length-probe: first call with null buffer returns the exact byte count,
      * second call fills the buffer. `string_to_utf8_chars` does NOT null-terminate.
      */
    def godotStringToScala(strBuf: Ptr[Byte]): String =
        if strBuf == null || !(strBuf.asInstanceOf[Ptr[Ptr[Byte]]]) == null then return ""
        if strToUtf8Fn == null then return ""
        val fn  = CFuncPtr.fromPtr[StringToUtf8Fn](strToUtf8Fn)
        val len = fn(strBuf, null, 0).toInt
        if len <= 0 then return ""
        Zone {
            val cstrBuf = alloc[Byte](len.toCSize)
            fn(strBuf, cstrBuf, len.toLong)
            val bytes = new Array[Byte](len)
            var i     = 0
            while i < len do
                bytes(i) = cstrBuf(i); i += 1
            new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
        }
    end godotStringToScala

    /** Extract the Godot String from a Variant into a caller-provided 8-byte buffer. */
    def extractStringFromVariant(variant: Ptr[Byte], dest: Ptr[Byte]): Unit =
        if variantToStrCtor != null then
            val fn = CFuncPtr.fromPtr[VFTCtorFn](variantToStrCtor)
            fn(dest, variant)
    end extractStringFromVariant

    /** Initialize a Godot StringName in a caller-provided 8-byte buffer from a CString. */
    def initStringName(buf: Ptr[Byte], s: CString): Unit =
        memset(buf, 0, StringNameSize)
        stringNameNewPtr(buf, s)

    /** Destroy a Godot String (releases the internal reference). */
    def destroyGodotString(buf: Ptr[Byte]): Unit = if strDestructorPtr != null then
        val dtor = CFuncPtr.fromPtr[PtrDestructorFn](strDestructorPtr)
        dtor(buf)

    /** Returns the singleton object pointer for a Godot singleton (e.g. Input, OS, Engine). */
    def getSingleton(name: CString): Ptr[Byte] =
        val snBuf = stackalloc[Byte](StringNameSize)
        memset(snBuf, 0, StringNameSize)
        stringNameNewPtr(snBuf, name)
        globalGetSingletonFn(snBuf)
    end getSingleton

    /** Prints a string to Godot's Output panel using the engine's print utility function. */
    def printString(s: String): Unit = Zone {
        if cachedPrintFn == null then return
        // Godot String is 8 bytes; build from CString then wrap in a Variant (24 bytes).
        val strBuf = stackalloc[Byte](8)
        memset(strBuf, 0, 8.toUSize)
        initGodotString(strBuf, toCString(s))
        val varBuf = stackalloc[Byte](24)
        memset(varBuf, 0, 24.toUSize)
        buildVariantFromString(variantFromStrCtor, varBuf, strBuf)
        val argsArr = stackalloc[Ptr[Byte]](1)
        argsArr(0) = varBuf
        callUtilityFunction(cachedPrintFn, argsArr, 1, null)
        destroyGodotString(strBuf)
    }

    /** Registers a property on an extension class with Godot's ClassDB.
      *
      * Must be called AFTER `classdb_register_extension_class2` for the same class. The class's
      * ClassCreationInfo2 must have set_func / get_func wired to handle this property name.
      *
      * `classNameSN` — heap-allocated StringName for the class (same buffer used at registration).
      * `nameSN` — heap-allocated StringName for the property name. `variantType` — one of the
      * `VariantType` constants.
      */
    def registerProperty(
        library: Ptr[Byte],
        classNameSN: Ptr[Byte],
        nameSN: Ptr[Byte],
        prop: PropertyDescriptor
    ): Unit = Zone {
        if registerPropertyFnAddr == null then return
        val fn           = CFuncPtr.fromPtr[RegisterPropertyFn](registerPropertyFnAddr)
        val info         = stackalloc[PropertyInfo]()
        val emptyClassSN = stackalloc[Byte](StringNameSize)
        val hintStrBuf   = stackalloc[Byte](8) // Godot String for hint_string
        val emptySetter  = stackalloc[Byte](StringNameSize)
        val emptyGetter  = stackalloc[Byte](StringNameSize)
        memset(info.asInstanceOf[Ptr[Byte]], 0, sizeof[PropertyInfo])
        memset(emptyClassSN, 0, StringNameSize)
        memset(hintStrBuf, 0, 8.toUSize)
        memset(emptySetter, 0, StringNameSize)
        memset(emptyGetter, 0, StringNameSize)
        info._1 = prop.variantType.toUInt
        info._2 = nameSN
        // class_name: used for NodeType/ResourceType hints to filter by class
        if prop.propClassName.nonEmpty then
            val propClassSN = stackalloc[Byte](StringNameSize)
            memset(propClassSN, 0, StringNameSize)
            stringNameNewPtr(propClassSN, toCString(prop.propClassName))
            info._3 = propClassSN
        else
            info._3 = emptyClassSN
        info._4 = prop.hint.toUInt
        // hint_string: a Godot String (8 bytes)
        if prop.hintString.nonEmpty then
            initGodotString(hintStrBuf, toCString(prop.hintString))
        info._5 = hintStrBuf
        info._6 = prop.usage.toUInt
        fn(library, classNameSN, info.asInstanceOf[Ptr[Byte]], emptySetter, emptyGetter)
        if prop.hintString.nonEmpty then destroyGodotString(hintStrBuf)
    }

    /** Register a class with Godot's ClassDB using the v3 API (Godot 4.3+, adds is_runtime).
      *
      * `infoRaw` must be a malloc'd 160-byte buffer filled with the ClassCreationInfo3 fields by
      * byte offset (see ClassRegistrar). Godot retains a pointer to it for the class lifetime.
      */
    def registerClass3(
        library: Ptr[Byte],
        classNameSN: Ptr[Byte],
        parentNameSN: Ptr[Byte],
        infoRaw: Ptr[Byte]
    ): Unit =
        if registerClass3FnPtr == null then return
        registerClass3FnPtr(library, classNameSN, parentNameSN, infoRaw)

    /** Returns a non-null pointer iff `classNameSN` is currently registered in ClassDB.
      *
      * Used for hot-reload detection: a non-null result means the class exists from a previous
      * `.so` load and this init pass is a hot-reload, not a fresh start.
      */
    def getClassTag(classNameSN: Ptr[Byte]): Ptr[Byte] =
        if getClassTagFnPtr == null then null
        else getClassTagFnPtr(classNameSN)

    /** Register an inspector group header on an already-registered extension class. */
    def registerPropertyGroup(
        library: Ptr[Byte],
        classNameSN: Ptr[Byte],
        groupName: String,
        prefix: String
    ): Unit = Zone {
        if registerPropertyGroupFnAddr == null then return
        // CFuncPtr4[library, className_SN, groupName_GString, prefix_GString, Unit]
        val fn      = CFuncPtr.fromPtr[CFuncPtr4[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]](
          registerPropertyGroupFnAddr
        )
        val nameBuf = stackalloc[Byte](8)
        val prefBuf = stackalloc[Byte](8)
        memset(nameBuf, 0, 8.toUSize)
        memset(prefBuf, 0, 8.toUSize)
        initGodotString(nameBuf, toCString(groupName))
        initGodotString(prefBuf, toCString(prefix))
        fn(library, classNameSN, nameBuf, prefBuf)
        destroyGodotString(nameBuf)
        destroyGodotString(prefBuf)
    }

    /** Register an inspector subgroup header on an already-registered extension class. */
    def registerPropertySubgroup(
        library: Ptr[Byte],
        classNameSN: Ptr[Byte],
        subgroupName: String,
        prefix: String
    ): Unit = Zone {
        if registerPropertySubgroupFnAddr == null then return
        val fn      = CFuncPtr.fromPtr[CFuncPtr4[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]](
          registerPropertySubgroupFnAddr
        )
        val nameBuf = stackalloc[Byte](8)
        val prefBuf = stackalloc[Byte](8)
        memset(nameBuf, 0, 8.toUSize)
        memset(prefBuf, 0, 8.toUSize)
        initGodotString(nameBuf, toCString(subgroupName))
        initGodotString(prefBuf, toCString(prefix))
        fn(library, classNameSN, nameBuf, prefBuf)
        destroyGodotString(nameBuf)
        destroyGodotString(prefBuf)
    }

    /** Register an inspector category header via a fake NIL property with USAGE_CATEGORY. */
    def registerPropertyCategory(
        library: Ptr[Byte],
        classNameSN: Ptr[Byte],
        name: String
    ): Unit = Zone {
        if registerPropertyFnAddr == null then return
        val fn         = CFuncPtr.fromPtr[RegisterPropertyFn](registerPropertyFnAddr)
        val nameSN     = stackalloc[Byte](StringNameSize)
        val emptySN    = stackalloc[Byte](StringNameSize)
        val hintStrBuf = stackalloc[Byte](8)
        val info       = stackalloc[PropertyInfo]()
        memset(nameSN, 0, StringNameSize)
        memset(emptySN, 0, StringNameSize)
        memset(hintStrBuf, 0, 8.toUSize)
        memset(info.asInstanceOf[Ptr[Byte]], 0, sizeof[PropertyInfo])
        stringNameNewPtr(nameSN, toCString(name))
        info._1 = 0.toUInt                      // type = NIL
        info._2 = nameSN
        info._3 = emptySN                       // class_name = empty
        info._4 = 0.toUInt                      // hint = None
        info._5 = hintStrBuf                    // hint_string = ""
        info._6 = PropertyUsage.Category.toUInt // USAGE_CATEGORY = 128
        fn(library, classNameSN, info.asInstanceOf[Ptr[Byte]], emptySN, emptySN)
    }

    /** Register a signal on an already-registered extension class.
      *
      * `paramInfos` — pointer to a heap-allocated `PropertyInfo[paramCount]` array describing the
      * signal's typed parameters, or null for a no-argument signal. Godot uses this to expose the
      * signal signature in the editor and to validate typed connections.
      */
    def registerSignal(
        library: Ptr[Byte],
        classNameSN: Ptr[Byte],
        signalNameSN: Ptr[Byte],
        paramInfos: Ptr[Byte] = null,
        paramCount: Long = 0L
    ): Unit =
        if registerSignalFnPtr == null then return
        registerSignalFnPtr(library, classNameSN, signalNameSN, paramInfos, paramCount)

    /** Emit a signal by name from a Godot object.
      *
      * Builds a StringName Variant for `signalName` and calls `Object::emit_signal` via ptrcall.
      * For extension class signals with no arguments this is sufficient. Typed signal payloads
      * will be added in a later phase.
      */
    def emitSignal(objectPtr: Ptr[Byte], signalName: String): Unit =
        if emitSignalMethodBind == null then return
        val args = stackalloc[Ptr[Byte]](1)
        args(0) = StringNames.cached(signalName)
        ptrcallPtr(emitSignalMethodBind, objectPtr, args, null)

    /** Registers a callable method on an extension class with Godot's ClassDB.
      *
      * Must be called AFTER `classdb_register_extension_class2` for the same class.
      *
      * `classNameSN` — heap-allocated StringName for the class. `nameSN` — heap-allocated
      * StringName for the method. `methodUserdata` — pointer passed back to `callFn` as its first
      * argument (used to identify which Scala dispatch function to invoke). `callFn` — the shared
      * CFuncPtr that dispatches the call.
      */
    def registerMethod(
        library: Ptr[Byte],
        classNameSN: Ptr[Byte],
        nameSN: Ptr[Byte],
        methodUserdata: Ptr[Byte],
        callFn: Ptr[Byte],
        hasReturnValue: Boolean = false,
        returnVariantType: Int = 0,
        argumentCount: Int = 0
    ): Unit =
        if registerMethodFnAddr == null then return
        val fn      = CFuncPtr.fromPtr[RegisterMethodFn](registerMethodFnAddr)
        val info    = stackalloc[ClassMethodInfo]()
        val emptySN = StringNames.cached("") // valid empty StringName, safe to share
        // An 8-byte zeroed buffer is a valid empty Godot String (default-constructed).
        val emptyStr = malloc(8).asInstanceOf[Ptr[Byte]]
        memset(emptyStr, 0, 8.toUSize)

        memset(info.asInstanceOf[Ptr[Byte]], 0, sizeof[ClassMethodInfo])
        info._1 = nameSN         // name
        info._2 = methodUserdata // method_userdata
        info._3 = callFn         // call_func
        info._4 = null           // ptrcall_func (unused)
        info._5 = 1.toUInt       // method_flags: GDEXTENSION_METHOD_FLAG_NORMAL
        info._6 = (if hasReturnValue then 1 else 0).toUByte

        if hasReturnValue then
            val retInfo = malloc(sizeof[PropertyInfo]).asInstanceOf[Ptr[PropertyInfo]]
            memset(retInfo.asInstanceOf[Ptr[Byte]], 0, sizeof[PropertyInfo])
            retInfo._1 = returnVariantType.toUInt
            retInfo._2 = emptySN   // name  — must be a valid StringName, not null
            retInfo._3 = emptySN   // class_name
            retInfo._5 = emptyStr  // hint_string — empty Godot String buffer
            retInfo._6 = 6.toUInt  // usage = Default
            info._7 = retInfo.asInstanceOf[Ptr[Byte]]
        end if

        info._9 = argumentCount.toUInt
        if argumentCount > 0 then
            // arguments_info: Godot indexes into this array for each arg even in release builds.
            // Provide PropertyInfo entries with valid (empty) StringNames; type stays NIL (0)
            // since we don't yet carry per-arg type info through MethodEntry.
            val argsInfo = malloc(sizeof[PropertyInfo] * argumentCount.toUSize)
                .asInstanceOf[Ptr[PropertyInfo]]
            memset(argsInfo.asInstanceOf[Ptr[Byte]], 0, sizeof[PropertyInfo] * argumentCount.toUSize)
            for i <- 0 until argumentCount do
                (argsInfo + i)._2 = emptySN   // name — must not be null
                (argsInfo + i)._3 = emptySN   // class_name
                (argsInfo + i)._5 = emptyStr  // hint_string
                (argsInfo + i)._6 = 6.toUInt  // usage
            info._10 = argsInfo.asInstanceOf[Ptr[Byte]]

            // arguments_metadata: zeroed = GDEXTENSION_METHOD_ARGUMENT_METADATA_NONE
            val metaBuf = malloc((argumentCount * 4).toUSize)
            memset(metaBuf, 0, (argumentCount * 4).toUSize)
            info._11 = metaBuf
        end if

        fn(library, classNameSN, info.asInstanceOf[Ptr[Byte]])
    end registerMethod

    /** Unregisters an extension class from Godot's ClassDB.
      *
      * Must be called during `deinitialize(SCENE)` for every class that was registered with
      * `classdb_register_extension_class2`. Children must be unregistered before parents.
      */
    def unregisterClass(library: Ptr[Byte], classNameSN: Ptr[Byte]): Unit =
        if unregisterClassFnPtr != null then unregisterClassFnPtr(library, classNameSN)

    /** Initialize an empty PackedStringArray in a caller-provided buffer (16 bytes on float_64). */
    def initPackedStringArray(buf: Ptr[Byte]): Unit =
        if pktStrArrayCtorPtr == null then return
        val ctor = CFuncPtr.fromPtr[PtrTypeCtorFn](pktStrArrayCtorPtr)
        ctor(buf, null)
    end initPackedStringArray

    /** Append a CString value to an already-initialized PackedStringArray. */
    def packedStringArrayAppendCString(arr: Ptr[Byte], s: CString): Unit =
        if pktStrArrayPushBackPtr == null then return
        val strBuf = stackalloc[Byte](8) // Godot String (8 bytes on float_64)
        memset(strBuf, 0, 8.toUSize)
        stringNewFn(strBuf, s)
        val args = stackalloc[Ptr[Byte]](1)
        args(0) = strBuf
        val retBuf = stackalloc[Byte](8) // bool return value (discarded)
        val fn     = CFuncPtr.fromPtr[BuiltinMethodFn](pktStrArrayPushBackPtr)
        fn(arr, args, retBuf, 1)
        if strDestructorPtr != null then
            val dtor = CFuncPtr.fromPtr[PtrDestructorFn](strDestructorPtr)
            dtor(strBuf)
    end packedStringArrayAppendCString

    /** Initialize a Godot NodePath in a caller-provided 8-byte buffer from a Godot String buffer.
      */
    def initNodePath(buf: Ptr[Byte], strBuf: Ptr[Byte]): Unit =
        if nodepathFromStrCtorPtr == null then return
        val args = stackalloc[Ptr[Byte]](1)
        args(0) = strBuf
        callNodePathCtor(nodepathFromStrCtorPtr, buf, args)
    end initNodePath

    /** Destroy a Godot NodePath (releases the internal reference). */
    def destroyNodePath(buf: Ptr[Byte]): Unit =
        if nodepathDestructorPtr == null then return
        val dtor = CFuncPtr.fromPtr[PtrDestructorFn](nodepathDestructorPtr)
        dtor(buf)
    end destroyNodePath

    // CFuncPtr calls must not be inlined inside Zone{} blocks under -source:future;
    // wrap them in named methods called from Zone context instead.
    private def callNodePathCtor(ctor: Ptr[Byte], dest: Ptr[Byte], args: Ptr[Ptr[Byte]]): Unit =
        val fn = CFuncPtr.fromPtr[NodePathCtorFn](ctor)
        fn(dest, args)

    /** Initialize a 24-byte Variant buffer from an 8-byte Godot String buffer. `dest` must be
      * zeroed; `src` must be a valid Godot String (from [[initGodotString]]).
      */
    def buildStringVariant(dest: Ptr[Byte], src: Ptr[Byte]): Unit =
        buildVariantFromString(variantFromStrCtor, dest, src)

    private def buildVariantFromString(ctor: Ptr[Byte], dest: Ptr[Byte], src: Ptr[Byte]): Unit =
        val fn = CFuncPtr.fromPtr[VFTCtorFn](ctor)
        fn(dest, src)

    /** Cast an object pointer to a different class, returning null if the cast fails. */
    def objectCastTo(obj: Ptr[Byte], classTag: Ptr[Byte]): Ptr[Byte] =
        if objectCastToFnPtr == null then null
        else objectCastToFnPtr(obj, classTag)

    /** Return the engine instance ID for an object (0 for null). */
    def objectGetInstanceId(obj: Ptr[Byte]): Long =
        if objectGetInstanceIdFnPtr == null then 0L
        else objectGetInstanceIdFnPtr(obj)

    /** Destroy a manually-managed (non-RefCounted) object. */
    def objectDestroy(obj: Ptr[Byte]): Unit =
        if objectDestroyFnPtr != null then objectDestroyFnPtr(obj)
end GdxApi
