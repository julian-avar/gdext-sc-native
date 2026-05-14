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

private type GetUtilityFnPtrFn  = CFuncPtr2[Ptr[Byte], Long, Ptr[Byte]]
private type UtilityCallFn      = CFuncPtr3[Ptr[Byte], Ptr[Ptr[Byte]], CInt, Unit]
private type StringNewFn        = CFuncPtr2[Ptr[Byte], CString, Unit]
private type GetVFTCtorFn       = CFuncPtr1[CUnsignedInt, Ptr[Byte]]
private type VFTCtorFn          = CFuncPtr2[Ptr[Byte], Ptr[Byte], Unit]
private type GetPtrDestructorFn = CFuncPtr1[CUnsignedInt, Ptr[Byte]]
private type PtrDestructorFn    = CFuncPtr1[Ptr[Byte], Unit]
// variant_get_ptr_constructor(type, index) → constructor fn
private type GetPtrCtorFn = CFuncPtr2[CUnsignedInt, CInt, Ptr[Byte]]
// NodePath ptr constructor: (dest, args) → Unit
private type NodePathCtorFn   = CFuncPtr2[Ptr[Byte], Ptr[Ptr[Byte]], Unit]
private type VariantDestroyFn = CFuncPtr1[Ptr[Byte], Unit]
// p_o, p_classname (StringName*), p_instance
private type ObjectSetInstanceFn = CFuncPtr3[Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]
// callable_custom_create2(r_callable, p_info)
private type CallableCreateFn = CFuncPtr2[Ptr[Byte], Ptr[Byte], Unit]
private type GetSingletonFn   = CFuncPtr1[Ptr[Byte], Ptr[Byte]]

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
    private var variantFromStrCtor: Ptr[Byte]            = null
    private var strDestructorPtr: Ptr[Byte]              = null
    private var nodepathFromStrCtorPtr: Ptr[Byte]        = null
    private var nodepathDestructorPtr: Ptr[Byte]         = null
    private var variantDestroyPtr: Ptr[Byte]             = null
    private var cachedPrintFn: Ptr[Byte]                 = null
    private var globalGetSingletonFn: GetSingletonFn     = scala.compiletime.uninitialized
    private var registerPropertyFnAddr: Ptr[Byte]        = null

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
        if vftCtorGetAddr != null then
            val vftCtor: GetVFTCtorFn = CFuncPtr.fromPtr[GetVFTCtorFn](vftCtorGetAddr)
            variantFromStrCtor = vftCtor(4.toUInt)
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

        registerPropertyFnAddr = getProcAddr(c"classdb_register_extension_class_property")

        // Cache the print utility function pointer (needs both functions loaded)
        if getUtilFnAddr != null && snNewAddr != null then
            val snBuf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
            memset(snBuf, 0, StringNameSize)
            stringNameNewPtr(snBuf, c"print")
            cachedPrintFn = getUtilFnPtr(snBuf, 2648703342L)
            free(snBuf)
        end if

        // Cache Object::connect method bind for connectSignal()
        if getMethodBindAddr != null && snNewAddr != null then
            val objSN  = stackalloc[Byte](StringNameSize)
            val connSN = stackalloc[Byte](StringNameSize)
            memset(objSN, 0, StringNameSize)
            memset(connSN, 0, StringNameSize)
            stringNameNewPtr(objSN, c"Object")
            stringNameNewPtr(connSN, c"connect")
            connectMethodBind = getMethodBindPtr(objSN, connSN, 1518946055L)
        end if
    end initialize

    def getMethodBind(className: CString, methodName: CString, hash: Long): MethodBindPtr =
        val cnBuf = stackalloc[Byte](StringNameSize)
        val mnBuf = stackalloc[Byte](StringNameSize)
        memset(cnBuf, 0, StringNameSize)
        memset(mnBuf, 0, StringNameSize)
        stringNameNewPtr(cnBuf, className)
        stringNameNewPtr(mnBuf, methodName)
        getMethodBindPtr(cnBuf, mnBuf, hash)
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
        variantType: Int
    ): Unit =
        if registerPropertyFnAddr == null then return
        val fn           = CFuncPtr.fromPtr[RegisterPropertyFn](registerPropertyFnAddr)
        val info         = stackalloc[PropertyInfo]()
        val emptyClassSN = stackalloc[Byte](StringNameSize) // class_name — must be non-null
        val emptyHintStr =
            stackalloc[Byte](8) // hint_string (Godot String, 8 bytes) — must be non-null
        val emptySetter  = stackalloc[Byte](StringNameSize)
        val emptyGetter  = stackalloc[Byte](StringNameSize)
        memset(info.asInstanceOf[Ptr[Byte]], 0, sizeof[PropertyInfo])
        memset(emptyClassSN, 0, StringNameSize)
        memset(emptyHintStr, 0, 8.toUSize)
        memset(emptySetter, 0, StringNameSize)
        memset(emptyGetter, 0, StringNameSize)
        info._1 = variantType.toUInt // type
        info._2 = nameSN             // name
        info._3 = emptyClassSN       // class_name → zeroed StringName (not a null ptr)
        // _4 hint: stays 0 = PROPERTY_HINT_NONE
        info._5 = emptyHintStr // hint_string → zeroed String (not a null ptr)
        info._6 = 6.toUInt     // usage: PROPERTY_USAGE_STORAGE | PROPERTY_USAGE_EDITOR
        fn(library, classNameSN, info.asInstanceOf[Ptr[Byte]], emptySetter, emptyGetter)
    end registerProperty

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

    private def buildVariantFromString(ctor: Ptr[Byte], dest: Ptr[Byte], src: Ptr[Byte]): Unit =
        val fn = CFuncPtr.fromPtr[VFTCtorFn](ctor)
        fn(dest, src)
end GdxApi
