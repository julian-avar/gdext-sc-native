package gdext

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*
// import gdext.generated.UtilityFunctions

type MethodBindPtr   = Ptr[Byte]
type GetMethodBindFn = CFuncPtr3[CString, CString, Long, MethodBindPtr]
type PtrcallFn       =
    CFuncPtr6[MethodBindPtr, Ptr[Byte], Ptr[Ptr[Byte]], CInt, Ptr[Byte], Ptr[Byte], Unit]
type ConstructObjectFn = CFuncPtr1[CString, Ptr[Byte]]

private type GetUtilityFnPtrFn    = CFuncPtr2[Ptr[Byte], Long, Ptr[Byte]]
private type UtilityCallFn        = CFuncPtr3[Ptr[Byte], Ptr[Ptr[Byte]], CInt, Unit]
private type StringNewFn          = CFuncPtr2[Ptr[Byte], CString, Unit]
private type GetVFTCtorFn         = CFuncPtr1[CUnsignedInt, Ptr[Byte]]
private type VFTCtorFn            = CFuncPtr2[Ptr[Byte], Ptr[Byte], Unit]
private type GetPtrDestructorFn   = CFuncPtr1[CUnsignedInt, Ptr[Byte]]
private type PtrDestructorFn      = CFuncPtr1[Ptr[Byte], Unit]
private type VariantDestroyFn     = CFuncPtr1[Ptr[Byte], Unit]
// p_o, p_classname (StringName*), p_instance
private type ObjectSetInstanceFn  = CFuncPtr3[Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]

object GdxApi:
    private var getMethodBindPtr: GetMethodBindFn       = scala.compiletime.uninitialized
    private var ptrcallPtr: PtrcallFn                   = scala.compiletime.uninitialized
    private var constructObjectPtr: ConstructObjectFn   = scala.compiletime.uninitialized
    private var objectSetInstanceFn: ObjectSetInstanceFn = scala.compiletime.uninitialized
    private var getUtilFnPtr: GetUtilityFnPtrFn         = scala.compiletime.uninitialized
    private var stringNameNewPtr: StringNameNewFn       = scala.compiletime.uninitialized
    private var stringNewFn: StringNewFn                = scala.compiletime.uninitialized
    private var variantFromStrCtor: Ptr[Byte]           = null
    private var strDestructorPtr: Ptr[Byte]             = null
    private var variantDestroyPtr: Ptr[Byte]            = null
    private var cachedPrintFn: Ptr[Byte]                = null

    private[gdext] def initialize(getProcAddr: GetProcAddressFn): Unit =
        val getMethodBindAddr      = getProcAddr(c"classdb_get_method_bind")
        val ptrcallAddr            = getProcAddr(c"object_method_bind_call")
        val constructObjectAddr    = getProcAddr(c"classdb_construct_object")
        val objectSetInstanceAddr  = getProcAddr(c"object_set_instance")
        val getUtilFnAddr       = getProcAddr(c"variant_get_ptr_utility_function")
        val snNewAddr           = getProcAddr(c"string_name_new_with_utf8_chars")
        val strNewAddr          = getProcAddr(c"string_new_with_utf8_chars")
        val vftCtorGetAddr      = getProcAddr(c"get_variant_from_type_constructor")
        val ptrDtorGetAddr      = getProcAddr(c"variant_get_ptr_destructor")
        val vDestroyAddr        = getProcAddr(c"variant_destroy")

        if getMethodBindAddr != null then
            getMethodBindPtr = CFuncPtr.fromPtr[GetMethodBindFn](getMethodBindAddr)
        if ptrcallAddr != null then ptrcallPtr = CFuncPtr.fromPtr[PtrcallFn](ptrcallAddr)
        if constructObjectAddr != null then
            constructObjectPtr = CFuncPtr.fromPtr[ConstructObjectFn](constructObjectAddr)
        if objectSetInstanceAddr != null then
            objectSetInstanceFn = CFuncPtr.fromPtr[ObjectSetInstanceFn](objectSetInstanceAddr)
        if getUtilFnAddr != null then
            getUtilFnPtr = CFuncPtr.fromPtr[GetUtilityFnPtrFn](getUtilFnAddr)
        if snNewAddr != null then stringNameNewPtr = CFuncPtr.fromPtr[StringNameNewFn](snNewAddr)
        if strNewAddr != null then stringNewFn = CFuncPtr.fromPtr[StringNewFn](strNewAddr)
        if vftCtorGetAddr != null then
            val vftCtor: GetVFTCtorFn = CFuncPtr.fromPtr[GetVFTCtorFn](vftCtorGetAddr)
            variantFromStrCtor = vftCtor(4.toUInt)
        if ptrDtorGetAddr != null then
            val ptrDtor: GetPtrDestructorFn = CFuncPtr.fromPtr[GetPtrDestructorFn](ptrDtorGetAddr)
            strDestructorPtr = ptrDtor(4.toUInt)
        if vDestroyAddr != null then variantDestroyPtr = vDestroyAddr
        // Cache the print utility function pointer (needs both functions loaded)
        if getUtilFnAddr != null && snNewAddr != null then
            val snBuf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
            memset(snBuf, 0, StringNameSize)
            stringNameNewPtr(snBuf, c"print")
            cachedPrintFn = getUtilFnPtr(snBuf, 2648703342L)
            free(snBuf)
        end if
    end initialize

    def getMethodBind(className: CString, methodName: CString, hash: Long): MethodBindPtr =
        getMethodBindPtr(className, methodName, hash)

    def ptrcall(
        methodBind: MethodBindPtr,
        instance: Ptr[Byte],
        args: Ptr[Ptr[Byte]],
        ret: Ptr[Byte]
    ): Unit =
        val argCount: CInt = if args == null then 0 else Int.MaxValue
        ptrcallPtr(methodBind, instance, args, argCount, ret, null)
    end ptrcall

    def constructObject(className: CString): Ptr[Byte] = constructObjectPtr(className)

    /** Registers a binding between a Godot Object and an extension class instance.
      * `classNameSN` must be a StringName buffer for the registered extension class name.
      * `instancePtr` is the opaque pointer Godot will pass back to virtual call functions.
      */
    def setInstance(godotPtr: Ptr[Byte], classNameSN: Ptr[Byte], instancePtr: Ptr[Byte]): Unit =
        objectSetInstanceFn(godotPtr, classNameSN, instancePtr)

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

    /** Prints a string to Godot's output using the print utility function. */
    def printString(s: String): Unit = {
        // Temporarily use println for testing
        println(s)
    }
    end printString
end GdxApi
