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

private type GetUtilityFnPtrFn = CFuncPtr2[Ptr[Byte], Long, Ptr[Byte]]
private type UtilityCallFn     = CFuncPtr3[Ptr[Byte], Ptr[Ptr[Byte]], CInt, Unit]

object GdxApi:
    private var getMethodBindPtr: GetMethodBindFn     = scala.compiletime.uninitialized
    private var ptrcallPtr: PtrcallFn                 = scala.compiletime.uninitialized
    private var constructObjectPtr: ConstructObjectFn = scala.compiletime.uninitialized
    private var getUtilFnPtr: GetUtilityFnPtrFn       = scala.compiletime.uninitialized
    private var stringNameNewPtr: StringNameNewFn     = scala.compiletime.uninitialized

    private[gdext] def initialize(getProcAddr: GetProcAddressFn): Unit =
        val getMethodBindAddr   = getProcAddr(c"classdb_get_method_bind")
        val ptrcallAddr         = getProcAddr(c"object_method_bind_call")
        val constructObjectAddr = getProcAddr(c"classdb_construct_object")
        val getUtilFnAddr       = getProcAddr(c"variant_get_ptr_utility_function")
        val snNewAddr           = getProcAddr(c"string_name_new_with_utf8_chars")

        if getMethodBindAddr != null then
            getMethodBindPtr = CFuncPtr.fromPtr[GetMethodBindFn](getMethodBindAddr)
        if ptrcallAddr != null then ptrcallPtr = CFuncPtr.fromPtr[PtrcallFn](ptrcallAddr)
        if constructObjectAddr != null then
            constructObjectPtr = CFuncPtr.fromPtr[ConstructObjectFn](constructObjectAddr)
        if getUtilFnAddr != null then
            getUtilFnPtr = CFuncPtr.fromPtr[GetUtilityFnPtrFn](getUtilFnAddr)
        if snNewAddr != null then stringNameNewPtr = CFuncPtr.fromPtr[StringNameNewFn](snNewAddr)
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
    def printString(s: String): Unit =
        println(s"GdxApi.printString called with: $s")
        // Convert Scala string to CString (null-terminated UTF-8 bytes)
        val sBytes        = s.getBytes("UTF-8")
        val cstrStackSize = sBytes.length + 1 // +1 for null terminator
        val cstr          = stackalloc[Byte](cstrStackSize)
        // Copy string bytes and add null terminator
        Array.copy(sBytes, 0, cstr, 0, sBytes.length)
        cstr(sBytes.length) = 0.toByte // null terminator

        // Convert CString to Godot Variant (String) - duplicate logic from UtilityFunctions.strToVar
        // generated.strToVar(cstr, _ret)
        val _args = stackalloc[Ptr[Byte]](1)
        _args(0) = cstr
        val _ret = stackalloc[Ptr[Byte]]()
        GdxApi.callUtilityFunction(
          getUtilityFunctionPtr(c"str_to_var", 1891498491L),
          _args,
          1,
          _ret.asInstanceOf[Ptr[Byte]]
        )
        val variant = !_ret

        // Prepare arguments for vararg function: array of Variant pointers, null-terminated
        val args = stackalloc[Ptr[Byte]](2) // 1 for the variant, 1 for null terminator
        args(0) = variant
        args(1) = null.asInstanceOf[Ptr[Byte]]

        // Call Godot's print utility function (vararg, so argCount = -1)
        callUtilityFunction(getUtilityFunctionPtr(c"print", 2648703342L), args, -1, null)
        println("Finished GdxApi.printString call")
    end printString
end GdxApi
