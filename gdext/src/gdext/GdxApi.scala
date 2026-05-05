package gdext

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

type MethodBindPtr = Ptr[Byte]
type GetMethodBindFn = CFuncPtr3[CString, CString, Long, MethodBindPtr]
type PtrcallFn = CFuncPtr6[MethodBindPtr, Ptr[Byte], Ptr[Ptr[Byte]], CInt, Ptr[Byte], Ptr[Byte], Unit]
type ConstructObjectFn = CFuncPtr1[CString, Ptr[Byte]]

object GdxApi:
    private var getMethodBindPtr: GetMethodBindFn = scala.compiletime.uninitialized
    private var ptrcallPtr: PtrcallFn = scala.compiletime.uninitialized
    private var constructObjectPtr: ConstructObjectFn = scala.compiletime.uninitialized

    private[gdext] def initialize(
        getProcAddr: GetProcAddressFn
    ): Unit =
        val getMethodBindAddr = getProcAddr(c"classdb_get_method_bind")
        val ptrcallAddr = getProcAddr(c"object_method_bind_call")
        val constructObjectAddr = getProcAddr(c"classdb_construct_object")

        if getMethodBindAddr != null then
            getMethodBindPtr = CFuncPtr.fromPtr[GetMethodBindFn](getMethodBindAddr)
        if ptrcallAddr != null then
            ptrcallPtr = CFuncPtr.fromPtr[PtrcallFn](ptrcallAddr)
        if constructObjectAddr != null then
            constructObjectPtr = CFuncPtr.fromPtr[ConstructObjectFn](constructObjectAddr)

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

    def constructObject(className: CString): Ptr[Byte] =
        constructObjectPtr(className)
end GdxApi
