package gdext

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*

type MethodBindPtr   = Ptr[Byte]
type GetMethodBindFn = CFuncPtr3[CString, CString, Long, MethodBindPtr]
type PtrcallFn       =
    CFuncPtr6[MethodBindPtr, Ptr[Byte], Ptr[Ptr[Byte]], CInt, Ptr[Byte], Ptr[Byte], Unit]
type ConstructObjectFn = CFuncPtr1[CString, Ptr[Byte]]

private type StringNameDestroyFn = CFuncPtr1[Ptr[Byte], Unit]
private type GetUtilityFnPtrFn   = CFuncPtr2[Ptr[Byte], Long, Ptr[Byte]]
private type UtilityCallFn       = CFuncPtr3[Ptr[Byte], Ptr[Ptr[Byte]], CInt, Unit]

object GdxApi:
    private var getMethodBindPtr: GetMethodBindFn                 = scala.compiletime.uninitialized
    private var ptrcallPtr: PtrcallFn                             = scala.compiletime.uninitialized
    private var constructObjectPtr: ConstructObjectFn             = scala.compiletime.uninitialized
    private var getUtilFnPtr: GetUtilityFnPtrFn                   = scala.compiletime.uninitialized
    private var stringNameNewPtr: StringNameNewFn                 = scala.compiletime.uninitialized
    private var stringNameDestroyPtr: StringNameDestroyFn = scala.compiletime.uninitialized

    private[gdext] def initialize(getProcAddr: GetProcAddressFn): Unit =
        val getMethodBindAddr   = getProcAddr(c"classdb_get_method_bind")
        val ptrcallAddr         = getProcAddr(c"object_method_bind_call")
        val constructObjectAddr = getProcAddr(c"classdb_construct_object")
        val getUtilFnAddr       = getProcAddr(c"variant_get_ptr_utility_function")
        val snNewAddr           = getProcAddr(c"string_name_new_with_utf8_chars")
        val snDestroyAddr       = getProcAddr(c"string_name_destroy")

        if getMethodBindAddr != null then
            getMethodBindPtr = CFuncPtr.fromPtr[GetMethodBindFn](getMethodBindAddr)
        if ptrcallAddr != null then ptrcallPtr = CFuncPtr.fromPtr[PtrcallFn](ptrcallAddr)
        if constructObjectAddr != null then
            constructObjectPtr = CFuncPtr.fromPtr[ConstructObjectFn](constructObjectAddr)
        if getUtilFnAddr != null then
            getUtilFnPtr = CFuncPtr.fromPtr[GetUtilityFnPtrFn](getUtilFnAddr)
        if snNewAddr != null then stringNameNewPtr = CFuncPtr.fromPtr[StringNameNewFn](snNewAddr)
        if snDestroyAddr != null then
            stringNameDestroyPtr = CFuncPtr.fromPtr[StringNameDestroyFn](snDestroyAddr)
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

    /** Look up a Godot utility function pointer by name and hash. Heap-allocates a StringName
      * temporarily; the returned Ptr[Byte] is stable and can be cached in a Binds object.
      */
    def getUtilityFunctionPtr(name: CString, hash: Long): Ptr[Byte] =
        val snBuf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
        memset(snBuf, 0, StringNameSize)
        stringNameNewPtr(snBuf, name)
        val fn = getUtilFnPtr(snBuf, hash)
        stringNameDestroyPtr(snBuf)
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
end GdxApi
