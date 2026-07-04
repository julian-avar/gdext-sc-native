package net.`julian-avar`.gdext.core

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
private type GetSingletonFn   = CFuncPtr1[Ptr[Byte], Ptr[Byte]]
// classdb_unregister_extension_class(library, classNameSN)
private type UnregisterClassFn = CFuncPtr2[Ptr[Byte], Ptr[Byte], Unit]
private type EditorPluginFn    = CFuncPtr1[Ptr[Byte], Unit]
// packed_xxx_array_operator_index: (p_self, p_index) → interior pointer to element
private[gdext] type PackedArrayIndexFn = CFuncPtr2[Ptr[Byte], Long, Ptr[Byte]]
// classdb_register_extension_class_signal(library, class_name, signal_name, arg_info, arg_count)
private type RegisterSignalFn = CFuncPtr5[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte], Long, Unit]
// object_method_bind_call(method_bind, instance, args: Ptr[Variant*], arg_count, ret_variant, error)
private type MethodBindCallFn =
    CFuncPtr6[Ptr[Byte], Ptr[Byte], Ptr[Ptr[Byte]], Long, Ptr[Byte], Ptr[Byte], Unit]
private type ObjectCastToFn        = CFuncPtr2[Ptr[Byte], Ptr[Byte], Ptr[Byte]]
private type ObjectGetInstanceIdFn = CFuncPtr1[Ptr[Byte], Long]
private type ObjectDestroyFn       = CFuncPtr1[Ptr[Byte], Unit]

/** Type-erased callback dispatched by the shared trampoline.
  *
  * Subclasses unmarshal Variant arguments and invoke the user-supplied Scala closure.
  */
private[gdext] sealed trait AnyCallback:
    def call(args: Ptr[Ptr[Byte]], argc: Long): Unit

private final class Callback0(fn: () => Unit) extends AnyCallback:
    def call(args: Ptr[Ptr[Byte]], argc: Long): Unit = fn()

private final class Callback1[A](fn: A => Unit, fv: FromVariant[A]) extends AnyCallback:
    def call(args: Ptr[Ptr[Byte]], argc: Long): Unit = if argc >= 1 then fn(fv.read(args(0)))

private final class Callback2[A, B](fn: (A, B) => Unit, fa: FromVariant[A], fb: FromVariant[B])
    extends AnyCallback:
    def call(args: Ptr[Ptr[Byte]], argc: Long): Unit =
        if argc >= 2 then fn(fa.read(args(0)), fb.read(args(1)))
end Callback2

private final class Callback3[A, B, C](
    fn: (A, B, C) => Unit,
    fa: FromVariant[A],
    fb: FromVariant[B],
    fc: FromVariant[C]
) extends AnyCallback:
    def call(args: Ptr[Ptr[Byte]], argc: Long): Unit =
        if argc >= 3 then fn(fa.read(args(0)), fb.read(args(1)), fc.read(args(2)))
end Callback3

/** Stable registry mapping Int IDs to typed callbacks, dispatched via a single static trampoline.
  *
  * Callbacks are kept alive by the map (GC root) and the Int ID is stored in a malloc'd 4-byte
  * buffer passed as callable userdata to Godot. Because Scala Native's immix GC is non-moving,
  * references inside closures (including `this`) remain indefinitely valid.
  *
  * Thread safety: `register` and `remove` are synchronized to protect against concurrent access to
  * `callbacks` and `nextId`. The trampoline (called from Godot's signal dispatch, which runs on the
  * main thread) reads from `callbacks` without synchronization — the map's entries are stable once
  * published via the synchronized block in `register`.
  */
private[gdext] object CallbackRegistry:
    private val callbacks = scala.collection.mutable.Map[Int, AnyCallback]()
    private var nextId    = 0
    private[gdext] def register(cb: AnyCallback): Int = this
        .synchronized { val id = nextId; nextId += 1; callbacks(id) = cb; id }

    /** Remove a previously registered callback by its ID. Callers should invoke this from
      * `disconnect` to prevent unbounded map growth.
      */
    private[gdext] def remove(id: Int): Unit = this.synchronized { callbacks.remove(id) }

    /** Single static trampoline — reads callback ID from userdata, dispatches to the callback. */
    val trampoline: CallableCallFn = CFuncPtr5
        .fromScalaFunction[Ptr[Byte], Ptr[Ptr[Byte]], Long, Ptr[Byte], Ptr[Byte], Unit] {
            (userdata, args, argc, _, rError) =>
                if rError != null then !rError.asInstanceOf[Ptr[Int]] = 0
                val id = !userdata.asInstanceOf[Ptr[Int]]
                callbacks.get(id).foreach(_.call(args, argc))
        }
end CallbackRegistry

private[gdext] object GdxApi:
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
    private var arrayCopyCtorPtr: Ptr[Byte]              = null
    private var dictCopyCtorPtr: Ptr[Byte]               = null
    private var snFromStrCtorPtr: Ptr[Byte]              = null
    private var snCopyCtorPtr: Ptr[Byte]                 = null
    private var snDestructorPtr: Ptr[Byte]               = null
    private var strFromSnCtorPtr: Ptr[Byte]              = null
    private var strFromNpCtorPtr: Ptr[Byte]              = null
    private var npCopyCtorPtr: Ptr[Byte]                 = null
    private var arrayDestructorPtr: Ptr[Byte]            = null
    private var dictDestructorPtr: Ptr[Byte]             = null
    private var cachedPrintFn: Ptr[Byte]                 = null
    private var cachedPrinterrFn: Ptr[Byte]              = null
    private var globalGetSingletonFn: GetSingletonFn     = scala.compiletime.uninitialized
    private var registerPropertyFnAddr: Ptr[Byte]        = null
    private var registerMethodFnAddr: Ptr[Byte]          = null
    private var unregisterClassFnPtr: UnregisterClassFn  = scala.compiletime.uninitialized
    private var pktStrArrayCtorPtr: Ptr[Byte]            = null
    private var pktStrArrayPushBackPtr: Ptr[Byte]        = null
    // Array (TYPE_ARRAY = 28) builtin method pointers
    private[gdext] var arrayDefaultCtorPtr: Ptr[Byte] = null
    private[gdext] var arraySizeFnPtr: Ptr[Byte]      = null
    private[gdext] var arrayGetFnPtr: Ptr[Byte]       = null
    private[gdext] var arraySetFnPtr: Ptr[Byte]       = null
    private[gdext] var arrayPushBackFnPtr: Ptr[Byte]  = null
    private[gdext] var arrayClearFnPtr: Ptr[Byte]     = null
    private[gdext] var arrayHasFnPtr: Ptr[Byte]       = null
    private[gdext] var arrayFindFnPtr: Ptr[Byte]      = null
    private[gdext] var arrayInsertFnPtr: Ptr[Byte]    = null
    private[gdext] var arrayRemoveAtFnPtr: Ptr[Byte]  = null
    private[gdext] var arraySortFnPtr: Ptr[Byte]      = null
    private[gdext] var arrayEraseFnPtr: Ptr[Byte]     = null
    // Dictionary (TYPE_DICTIONARY = 27) builtin method pointers
    private[gdext] var dictDefaultCtorPtr: Ptr[Byte]            = null
    private[gdext] var dictSizeFnPtr: Ptr[Byte]                 = null
    private[gdext] var dictHasFnPtr: Ptr[Byte]                  = null
    private[gdext] var dictGetFnPtr: Ptr[Byte]                  = null
    private[gdext] var dictSetFnPtr: Ptr[Byte]                  = null
    private[gdext] var dictEraseFnPtr: Ptr[Byte]                = null
    private[gdext] var dictClearFnPtr: Ptr[Byte]                = null
    private[gdext] var dictKeysFnPtr: Ptr[Byte]                 = null
    private[gdext] var dictValuesFnPtr: Ptr[Byte]               = null
    private var registerSignalFnPtr: RegisterSignalFn           = scala.compiletime.uninitialized
    private var methodBindCallPtr: MethodBindCallFn             = scala.compiletime.uninitialized
    private[gdext] var emitSignalMethodBind: Ptr[Byte]          = null
    private[gdext] var disconnectMethodBind: Ptr[Byte]          = null
    private var variantFromSnCtor: Ptr[Byte]                    = null
    private var variantFromNpCtor: Ptr[Byte]                    = null
    private var variantToSnCtor: Ptr[Byte]                      = null
    private var variantToNpCtor: Ptr[Byte]                      = null
    private var registerPropertyGroupFnAddr: Ptr[Byte]          = null
    private var registerPropertySubgroupFnAddr: Ptr[Byte]       = null
    private var registerClass3FnPtr: RegisterClass3Fn           = scala.compiletime.uninitialized
    private var getClassTagFnPtr: GetClassTagFn                 = scala.compiletime.uninitialized
    private var objectCastToFnPtr: ObjectCastToFn               = scala.compiletime.uninitialized
    private var objectGetInstanceIdFnPtr: ObjectGetInstanceIdFn = scala.compiletime.uninitialized
    private var objectDestroyFnPtr: ObjectDestroyFn             = scala.compiletime.uninitialized
    private[gdext] var initRefMethodBind: Ptr[Byte]             = null
    private[gdext] var unreferenceMethodBind: Ptr[Byte]         = null
    private var editorAddPluginFnPtr: EditorPluginFn            = scala.compiletime.uninitialized
    private var editorRemovePluginFnPtr: EditorPluginFn         = scala.compiletime.uninitialized
    private[gdext] var registerIconFnAddr: Ptr[Byte]            = null
    // Packed array operator_index (const) — (p_self, p_index) → interior element pointer
    private[gdext] var packedByteArrayIndexFn: PackedArrayIndexFn    = null
    private[gdext] var packedInt32ArrayIndexFn: PackedArrayIndexFn   = null
    private[gdext] var packedInt64ArrayIndexFn: PackedArrayIndexFn   = null
    private[gdext] var packedFloat32ArrayIndexFn: PackedArrayIndexFn = null
    private[gdext] var packedFloat64ArrayIndexFn: PackedArrayIndexFn = null
    private[gdext] var packedStringArrayIndexFn: PackedArrayIndexFn  = null
    private[gdext] var packedVector2ArrayIndexFn: PackedArrayIndexFn = null
    private[gdext] var packedVector3ArrayIndexFn: PackedArrayIndexFn = null
    private[gdext] var packedVector4ArrayIndexFn: PackedArrayIndexFn = null
    private[gdext] var packedColorArrayIndexFn: PackedArrayIndexFn   = null
    // Packed array size builtin methods (variant_get_ptr_builtin_method), indexed by variant type ID
    // Type IDs: 29=Byte,30=Int32,31=Int64,32=Float32,33=Float64,34=String,35=Vec2,36=Vec3,37=Color,38=Vec4
    private[gdext] val packedSizeFns = new Array[Ptr[Byte]](39)
    // Packed array destructors, indexed by variant type ID
    private[gdext] val packedDtors = new Array[Ptr[Byte]](39)

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
            variantFromSnCtor = vftCtor(21.toUInt) // Variant from StringName
            variantFromNpCtor = vftCtor(22.toUInt) // Variant from NodePath
        end if
        if vtfCtorGetAddr != null then
            val vtfCtor: GetVFTCtorFn = CFuncPtr.fromPtr[GetVFTCtorFn](vtfCtorGetAddr)
            variantToStrCtor = vtfCtor(4.toUInt)
            variantToSnCtor = vtfCtor(21.toUInt) // StringName from Variant
            variantToNpCtor = vtfCtor(22.toUInt) // NodePath from Variant
        end if
        if ptrDtorGetAddr != null then
            val ptrDtor: GetPtrDestructorFn = CFuncPtr.fromPtr[GetPtrDestructorFn](ptrDtorGetAddr)
            strDestructorPtr = ptrDtor(4.toUInt)       // String destructor
            nodepathDestructorPtr = ptrDtor(22.toUInt) // NodePath destructor
            snDestructorPtr = ptrDtor(21.toUInt)       // StringName destructor
            arrayDestructorPtr = ptrDtor(28.toUInt)    // Array destructor
            dictDestructorPtr = ptrDtor(27.toUInt)     // Dictionary destructor
        end if
        if vDestroyAddr != null then variantDestroyPtr = vDestroyAddr

        val ptrCtorGetAddr = getProcAddr(c"variant_get_ptr_constructor")
        if ptrCtorGetAddr != null then
            val ptrCtorGet = CFuncPtr.fromPtr[GetPtrCtorFn](ptrCtorGetAddr)
            nodepathFromStrCtorPtr = ptrCtorGet(22.toUInt, 2) // NodePath(from: String), index 2
            pktStrArrayCtorPtr = ptrCtorGet(34.toUInt, 0)     // PackedStringArray() default ctor
            arrayDefaultCtorPtr = ptrCtorGet(28.toUInt, 0)    // Array() default ctor
            arrayCopyCtorPtr = ptrCtorGet(28.toUInt, 1)       // Array(other) copy ctor
            dictDefaultCtorPtr = ptrCtorGet(27.toUInt, 0)     // Dictionary() default ctor
            dictCopyCtorPtr = ptrCtorGet(27.toUInt, 1)        // Dictionary(other) copy ctor
            // StringName (type 21) constructors
            snFromStrCtorPtr = ptrCtorGet(21.toUInt, 2) // StringName(from: String), index 2
            snCopyCtorPtr = ptrCtorGet(21.toUInt, 1)    // StringName(other) copy ctor
            // NodePath (type 22) copy constructor
            npCopyCtorPtr = ptrCtorGet(22.toUInt, 1) // NodePath(other) copy ctor
            // String (type 4) constructors from other variant types
            strFromSnCtorPtr = ptrCtorGet(4.toUInt, 2) // String(from: StringName), index 2
            strFromNpCtorPtr = ptrCtorGet(4.toUInt, 3) // String(from: NodePath), index 3
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
            arraySizeFnPtr = getBM(28.toUInt, snBuf, 3173160232L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"get")
            arrayGetFnPtr = getBM(28.toUInt, snBuf, 708700221L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"set")
            arraySetFnPtr = getBM(28.toUInt, snBuf, 3798478031L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"push_back")
            arrayPushBackFnPtr = getBM(28.toUInt, snBuf, 3316032543L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"clear")
            arrayClearFnPtr = getBM(28.toUInt, snBuf, 3218959716L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"has")
            arrayHasFnPtr = getBM(28.toUInt, snBuf, 3680194679L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"find")
            arrayFindFnPtr = getBM(28.toUInt, snBuf, 2336346817L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"insert")
            arrayInsertFnPtr = getBM(28.toUInt, snBuf, 3176316662L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"remove_at")
            arrayRemoveAtFnPtr = getBM(28.toUInt, snBuf, 2823966027L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"sort")
            arraySortFnPtr = getBM(28.toUInt, snBuf, 3218959716L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"erase")
            arrayEraseFnPtr = getBM(28.toUInt, snBuf, 3316032543L)

            // Dictionary (type 27) methods
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"size")
            dictSizeFnPtr = getBM(27.toUInt, snBuf, 3173160232L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"has")
            dictHasFnPtr = getBM(27.toUInt, snBuf, 3680194679L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"get")
            dictGetFnPtr = getBM(27.toUInt, snBuf, 2205440559L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"set")
            dictSetFnPtr = getBM(27.toUInt, snBuf, 2175348267L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"erase")
            dictEraseFnPtr = getBM(27.toUInt, snBuf, 1776646889L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"clear")
            dictClearFnPtr = getBM(27.toUInt, snBuf, 3218959716L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"keys")
            dictKeysFnPtr = getBM(27.toUInt, snBuf, 4144163970L)
            memset(snBuf, 0, StringNameSize); stringNameNewPtr(snBuf, c"values")
            dictValuesFnPtr = getBM(27.toUInt, snBuf, 4144163970L)
        end if

        GdxApiV47.init(getProcAddr)
        registerPropertyFnAddr = getProcAddr(c"classdb_register_extension_class_property")
        registerPropertyGroupFnAddr =
            getProcAddr(c"classdb_register_extension_class_property_group")
        registerPropertySubgroupFnAddr =
            getProcAddr(c"classdb_register_extension_class_property_subgroup")
        registerMethodFnAddr = getProcAddr(c"classdb_register_extension_class_method")
        val unregisterClassAddr = getProcAddr(c"classdb_unregister_extension_class")
        if unregisterClassAddr != null then
            unregisterClassFnPtr = CFuncPtr.fromPtr[UnregisterClassFn](unregisterClassAddr)

        // Cache print / printerr utility function pointers
        if getUtilFnAddr != null && snNewAddr != null then
            val snBuf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
            memset(snBuf, 0, StringNameSize)
            stringNameNewPtr(snBuf, c"print")
            cachedPrintFn = getUtilFnPtr(snBuf, 2648703342L)
            memset(snBuf, 0, StringNameSize)
            stringNameNewPtr(snBuf, c"printerr")
            cachedPrinterrFn = getUtilFnPtr(snBuf, 2648703342L)
            free(snBuf)
        end if

        // Cache Object::connect and Object::emit_signal method binds
        if getMethodBindAddr != null && snNewAddr != null then
            val objSN  = stackalloc[Byte](StringNameSize)
            val connSN = stackalloc[Byte](StringNameSize)
            val emitSN = stackalloc[Byte](StringNameSize)
            memset(objSN, 0, StringNameSize)
            memset(connSN, 0, StringNameSize)
            memset(emitSN, 0, StringNameSize)
            stringNameNewPtr(objSN, c"Object")
            stringNameNewPtr(connSN, c"connect")
            stringNameNewPtr(emitSN, c"emit_signal")
            val discSN = stackalloc[Byte](StringNameSize)
            memset(discSN, 0, StringNameSize)
            stringNameNewPtr(discSN, c"disconnect")
            connectMethodBind = getMethodBindPtr(objSN, connSN, 1518946055L)
            emitSignalMethodBind = getMethodBindPtr(objSN, emitSN, 4047867050L)
            disconnectMethodBind = getMethodBindPtr(objSN, discSN, 1874754934L)
        end if

        val objectCastToAddr = getProcAddr(c"object_cast_to")
        if objectCastToAddr != null then
            objectCastToFnPtr = CFuncPtr.fromPtr[ObjectCastToFn](objectCastToAddr)

        val objectGetInstanceIdAddr = getProcAddr(c"object_get_instance_id")
        if objectGetInstanceIdAddr != null then
            objectGetInstanceIdFnPtr = CFuncPtr
                .fromPtr[ObjectGetInstanceIdFn](objectGetInstanceIdAddr)

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
            initRefMethodBind = getMethodBindPtr(rcSN, irSN, 2240911060L)
            unreferenceMethodBind = getMethodBindPtr(rcSN, unrSN, 2240911060L)
        end if

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

        val editorAddPluginAddr = getProcAddr(c"editor_add_plugin")
        if editorAddPluginAddr != null then
            editorAddPluginFnPtr = CFuncPtr.fromPtr[EditorPluginFn](editorAddPluginAddr)
        val editorRemovePluginAddr = getProcAddr(c"editor_remove_plugin")
        if editorRemovePluginAddr != null then
            editorRemovePluginFnPtr = CFuncPtr.fromPtr[EditorPluginFn](editorRemovePluginAddr)

        def loadIndexFn(name: CString): PackedArrayIndexFn =
            val addr = getProcAddr(name)
            if addr == null then null else CFuncPtr.fromPtr[PackedArrayIndexFn](addr)
        packedByteArrayIndexFn = loadIndexFn(c"packed_byte_array_operator_index_const")
        packedInt32ArrayIndexFn = loadIndexFn(c"packed_int32_array_operator_index_const")
        packedInt64ArrayIndexFn = loadIndexFn(c"packed_int64_array_operator_index_const")
        packedFloat32ArrayIndexFn = loadIndexFn(c"packed_float32_array_operator_index_const")
        packedFloat64ArrayIndexFn = loadIndexFn(c"packed_float64_array_operator_index_const")
        packedStringArrayIndexFn = loadIndexFn(c"packed_string_array_operator_index_const")
        packedVector2ArrayIndexFn = loadIndexFn(c"packed_vector2_array_operator_index_const")
        packedVector3ArrayIndexFn = loadIndexFn(c"packed_vector3_array_operator_index_const")
        packedVector4ArrayIndexFn = loadIndexFn(c"packed_vector4_array_operator_index_const")
        packedColorArrayIndexFn = loadIndexFn(c"packed_color_array_operator_index_const")

        if ptrBuiltinMethodAddr != null && snNewAddr != null then
            val getBM  = CFuncPtr.fromPtr[GetPtrBuiltinMethodFn](ptrBuiltinMethodAddr)
            val snBuf2 = stackalloc[Byte](StringNameSize)
            // Size method — same hash for all packed array types
            val packedTypeIds = Array(29, 30, 31, 32, 33, 34, 35, 36, 37, 38)
            for typeId <- packedTypeIds do
                memset(snBuf2, 0, StringNameSize)
                stringNameNewPtr(snBuf2, c"size")
                packedSizeFns(typeId) = getBM(typeId.toUInt, snBuf2, 3173160232L)
            end for
        end if
        if ptrDtorGetAddr != null then
            val ptrDtor       = CFuncPtr.fromPtr[GetPtrDestructorFn](ptrDtorGetAddr)
            val packedTypeIds = Array(29, 30, 31, 32, 33, 34, 35, 36, 37, 38)
            for typeId <- packedTypeIds do packedDtors(typeId) = ptrDtor(typeId.toUInt)
        end if
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
    /** Build a custom Callable in `dest` (must be 16 zeroed bytes).
      *
      * The Callable routes invocations through `CallbackRegistry.trampoline`. The `userdata`
      * pointer is passed to the trampoline on every call — it typically points to a heap-allocated
      * `Int` holding a `CallbackRegistry` ID.
      *
      * Used by `connectSignal` (stack-allocated dest) and by `Callable.lambda` factories
      * (heap-allocated dest, caller owns the memory).
      */
    private[gdext] def buildCallable(dest: Ptr[Byte], userdata: Ptr[Byte]): Unit =
        val info = stackalloc[Byte](88.toUSize)
        memset(info, 0, 88.toUSize)
        val infoPtrs = info.asInstanceOf[Ptr[Ptr[Byte]]]
        infoPtrs(0) = userdata
        infoPtrs(1) = gdxLibrary
        infoPtrs(3) = CFuncPtr.toPtr(CallbackRegistry.trampoline).asInstanceOf[Ptr[Byte]]
        callableCreatePtr(dest, info)
    end buildCallable

    def connectSignal(
        obj: Ptr[Byte],
        signalCStr: CString,
        callFn: CallableCallFn,
        userdata: Ptr[Byte] = null
    ): Unit =
        val snBuf = stackalloc[Byte](StringNameSize)
        memset(snBuf, 0, StringNameSize)
        stringNameNewPtr(snBuf, signalCStr)

        val callableBuf = stackalloc[Byte](16)
        memset(callableBuf, 0, 16.toUSize)
        // connectSignal uses a custom callFn (not the standard trampoline) so we build it inline.
        val info = stackalloc[Byte](88.toUSize)
        memset(info, 0, 88.toUSize)
        val infoPtrs = info.asInstanceOf[Ptr[Ptr[Byte]]]
        infoPtrs(0) = userdata
        infoPtrs(1) = gdxLibrary
        infoPtrs(3) = CFuncPtr.toPtr(callFn).asInstanceOf[Ptr[Byte]]
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

    /** Prints a string to Godot's Output panel using the engine's `print` utility function. */
    def printString(s: String): Unit = Zone {
        if cachedPrintFn == null then return
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

    /** Prints an error string to Godot's Output panel using the engine's `printerr` utility. */
    def printErrString(s: String): Unit = Zone {
        if cachedPrinterrFn == null then return
        val strBuf = stackalloc[Byte](8)
        memset(strBuf, 0, 8.toUSize)
        initGodotString(strBuf, toCString(s))
        val varBuf = stackalloc[Byte](24)
        memset(varBuf, 0, 24.toUSize)
        buildVariantFromString(variantFromStrCtor, varBuf, strBuf)
        val argsArr = stackalloc[Ptr[Byte]](1)
        argsArr(0) = varBuf
        callUtilityFunction(cachedPrinterrFn, argsArr, 1, null)
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
        else info._3 = emptyClassSN
        end if
        info._4 = prop.hint.toUInt
        // hint_string: a Godot String (8 bytes)
        if prop.hintString.nonEmpty then initGodotString(hintStrBuf, toCString(prop.hintString))
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
    end registerClass3

    /** Returns a non-null pointer iff `classNameSN` is currently registered in ClassDB.
      *
      * Used for hot-reload detection: a non-null result means the class exists from a previous
      * `.so` load and this init pass is a hot-reload, not a fresh start.
      */
    def getClassTag(classNameSN: Ptr[Byte]): Ptr[Byte] =
        if getClassTagFnPtr == null then null else getClassTagFnPtr(classNameSN)

    /** Register an inspector group header on an already-registered extension class. */
    def registerPropertyGroup(
        library: Ptr[Byte],
        classNameSN: Ptr[Byte],
        groupName: String,
        prefix: String
    ): Unit = Zone {
        if registerPropertyGroupFnAddr == null then return
        // CFuncPtr4[library, className_SN, groupName_GString, prefix_GString, Unit]
        val fn = CFuncPtr.fromPtr[CFuncPtr4[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]](
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
        val fn = CFuncPtr.fromPtr[CFuncPtr4[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]](
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
    def registerPropertyCategory(library: Ptr[Byte], classNameSN: Ptr[Byte], name: String): Unit =
        Zone {
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
            info._1 = 0.toUInt // type = NIL
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
    end registerSignal

    /** Emit a no-arg signal by name via ptrcall. */
    def emitSignal(objectPtr: Ptr[Byte], signalName: String): Unit =
        if emitSignalMethodBind == null then return
        val args = stackalloc[Ptr[Byte]](1)
        args(0) = StringNames.cached(signalName)
        ptrcallPtr(emitSignalMethodBind, objectPtr, args, null)
    end emitSignal

    /** Build a 24-byte StringName Variant in `dest` from a cached StringName handle. */
    private def buildSnVariant(dest: Ptr[Byte], signalName: String): Unit =
        if variantFromSnCtor == null then return
        val fn = CFuncPtr.fromPtr[VFTCtorFn](variantFromSnCtor)
        fn(dest, StringNames.cached(signalName))
    end buildSnVariant

    /** Emit a signal with 1 typed Variant arg using method_bind_call (handles varargs). */
    private[gdext] def emitSignalArgs1(
        objectPtr: Ptr[Byte],
        signalName: String,
        a0: Ptr[Byte]
    ): Unit =
        if methodBindCallPtr == null || emitSignalMethodBind == null then return
        val snVar  = stackalloc[Byte](24)
        val args   = stackalloc[Ptr[Byte]](2)
        val retVar = stackalloc[Byte](24)
        val errBuf = stackalloc[Byte](12)
        memset(snVar, 0, 24.toUSize); memset(retVar, 0, 24.toUSize); memset(errBuf, 0, 12.toUSize)
        buildSnVariant(snVar, signalName)
        args(0) = snVar; args(1) = a0
        methodBindCallPtr(emitSignalMethodBind, objectPtr, args, 2L, retVar, errBuf)
    end emitSignalArgs1

    /** Emit a signal with 2 typed Variant args using method_bind_call. */
    private[gdext] def emitSignalArgs2(
        objectPtr: Ptr[Byte],
        signalName: String,
        a0: Ptr[Byte],
        a1: Ptr[Byte]
    ): Unit =
        if methodBindCallPtr == null || emitSignalMethodBind == null then return
        val snVar  = stackalloc[Byte](24)
        val args   = stackalloc[Ptr[Byte]](3)
        val retVar = stackalloc[Byte](24)
        val errBuf = stackalloc[Byte](12)
        memset(snVar, 0, 24.toUSize); memset(retVar, 0, 24.toUSize); memset(errBuf, 0, 12.toUSize)
        buildSnVariant(snVar, signalName)
        args(0) = snVar; args(1) = a0; args(2) = a1
        methodBindCallPtr(emitSignalMethodBind, objectPtr, args, 3L, retVar, errBuf)
    end emitSignalArgs2

    /** Emit a signal with 3 typed Variant args using method_bind_call. */
    private[gdext] def emitSignalArgs3(
        objectPtr: Ptr[Byte],
        signalName: String,
        a0: Ptr[Byte],
        a1: Ptr[Byte],
        a2: Ptr[Byte]
    ): Unit =
        if methodBindCallPtr == null || emitSignalMethodBind == null then return
        val snVar  = stackalloc[Byte](24)
        val args   = stackalloc[Ptr[Byte]](4)
        val retVar = stackalloc[Byte](24)
        val errBuf = stackalloc[Byte](12)
        memset(snVar, 0, 24.toUSize); memset(retVar, 0, 24.toUSize); memset(errBuf, 0, 12.toUSize)
        buildSnVariant(snVar, signalName)
        args(0) = snVar; args(1) = a0; args(2) = a1; args(3) = a2
        methodBindCallPtr(emitSignalMethodBind, objectPtr, args, 4L, retVar, errBuf)
    end emitSignalArgs3

    /** Disconnect a signal by reconstructing the Callable from the original userdata pointer.
      *
      * Godot compares custom callables by (callable_userdata, token) when equal_func is null. Using
      * the same udBuf and gdxLibrary as the original connect call produces a Callable that Godot
      * treats as equal, enabling correct disconnection.
      */
    private[gdext] def disconnectSignal(
        obj: Ptr[Byte],
        signalName: String,
        udBuf: Ptr[Byte]
    ): Unit =
        if disconnectMethodBind == null then return
        val info = stackalloc[Byte](88.toUSize)
        memset(info, 0, 88.toUSize)
        val infoPtrs = info.asInstanceOf[Ptr[Ptr[Byte]]]
        infoPtrs(0) = udBuf
        infoPtrs(1) = gdxLibrary
        infoPtrs(3) = CFuncPtr.toPtr(CallbackRegistry.trampoline).asInstanceOf[Ptr[Byte]]
        val callableBuf = stackalloc[Byte](16)
        memset(callableBuf, 0, 16.toUSize)
        callableCreatePtr(callableBuf, info)
        val args = stackalloc[Ptr[Byte]](2)
        args(0) = StringNames.cached(signalName)
        args(1) = callableBuf
        ptrcallPtr(disconnectMethodBind, obj, args, null)
    end disconnectSignal

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
            retInfo._2 = emptySN  // name  — must be a valid StringName, not null
            retInfo._3 = emptySN  // class_name
            retInfo._5 = emptyStr // hint_string — empty Godot String buffer
            retInfo._6 = 6.toUInt // usage = Default
            info._7 = retInfo.asInstanceOf[Ptr[Byte]]
        end if

        info._9 = argumentCount.toUInt
        if argumentCount > 0 then
            // arguments_info: Godot indexes into this array for each arg even in release builds.
            // Provide PropertyInfo entries with valid (empty) StringNames; type stays NIL (0)
            // since we don't yet carry per-arg type info through MethodEntry.
            val argsInfo = malloc(sizeof[PropertyInfo] * argumentCount.toUSize)
                .asInstanceOf[Ptr[PropertyInfo]]
            memset(
              argsInfo.asInstanceOf[Ptr[Byte]],
              0,
              sizeof[PropertyInfo] * argumentCount.toUSize
            )
            for i <- 0 until argumentCount do
                (argsInfo + i)._2 = emptySN  // name — must not be null
                (argsInfo + i)._3 = emptySN  // class_name
                (argsInfo + i)._5 = emptyStr // hint_string
                (argsInfo + i)._6 = 6.toUInt // usage
            end for
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

    /** Initialize a Godot StringName in a caller-provided 8-byte buffer from a Scala String. */
    def initStringNameFromStr(buf: Ptr[Byte], s: String)(using Zone): Unit =
        if snFromStrCtorPtr == null then return
        val strBuf = stackalloc[Byte](8)
        memset(strBuf, 0, 8.toUSize)
        stringNewFn(strBuf, toCString(s))
        val args = stackalloc[Ptr[Byte]](1)
        args(0) = strBuf
        callNodePathCtor(snFromStrCtorPtr, buf, args)
        if strDestructorPtr != null then callPtrDtor(strDestructorPtr, strBuf)
    end initStringNameFromStr

    /** Copy a StringName handle into a new 8-byte buffer. Caller owns the result (must destroySN).
      */
    def copyStringName(dest: Ptr[Byte], src: Ptr[Byte]): Unit =
        if snCopyCtorPtr == null then return
        val args = stackalloc[Ptr[Byte]](1); args(0) = src
        callNodePathCtor(snCopyCtorPtr, dest, args)
    end copyStringName

    def destroyStringName(buf: Ptr[Byte]): Unit =
        if snDestructorPtr == null || buf == null then return
        callPtrDtor(snDestructorPtr, buf)

    /** Convert a Godot StringName buffer to a Scala String via String(StringName) constructor. */
    def stringNameToScala(snBuf: Ptr[Byte]): String =
        if snBuf == null || strFromSnCtorPtr == null then return ""
        Zone {
            val strBuf = stackalloc[Byte](8)
            memset(strBuf, 0, 8.toUSize)
            val args = stackalloc[Ptr[Byte]](1); args(0) = snBuf
            callNodePathCtor(strFromSnCtorPtr, strBuf, args)
            val result = godotStringToScala(strBuf)
            if strDestructorPtr != null then callPtrDtor(strDestructorPtr, strBuf)
            result
        }
    end stringNameToScala

    /** Copy a NodePath handle into a new 8-byte buffer. */
    def copyNodePath(dest: Ptr[Byte], src: Ptr[Byte]): Unit =
        if npCopyCtorPtr == null then return
        val args = stackalloc[Ptr[Byte]](1); args(0) = src
        callNodePathCtor(npCopyCtorPtr, dest, args)
    end copyNodePath

    /** Convert a Godot NodePath buffer to a Scala String via String(NodePath) constructor. */
    def nodePathToScala(npBuf: Ptr[Byte]): String =
        if npBuf == null || strFromNpCtorPtr == null then return ""
        Zone {
            val strBuf = stackalloc[Byte](8)
            memset(strBuf, 0, 8.toUSize)
            val args = stackalloc[Ptr[Byte]](1); args(0) = npBuf
            callNodePathCtor(strFromNpCtorPtr, strBuf, args)
            val result = godotStringToScala(strBuf)
            if strDestructorPtr != null then callPtrDtor(strDestructorPtr, strBuf)
            result
        }
    end nodePathToScala

    def destroyArray(handle: Ptr[Byte]): Unit =
        if arrayDestructorPtr == null || handle == null then return
        callPtrDtor(arrayDestructorPtr, handle)

    def destroyDict(handle: Ptr[Byte]): Unit =
        if dictDestructorPtr == null || handle == null then return
        callPtrDtor(dictDestructorPtr, handle)

    def buildStringNameVariant(dest: Ptr[Byte], snHandle: Ptr[Byte]): Unit =
        if variantFromSnCtor == null then return
        callVFTCtor(variantFromSnCtor, dest, snHandle)

    def buildNodePathVariant(dest: Ptr[Byte], npHandle: Ptr[Byte]): Unit =
        if variantFromNpCtor == null then return
        callVFTCtor(variantFromNpCtor, dest, npHandle)

    def extractStringNameFromVariant(variant: Ptr[Byte], dest: Ptr[Byte]): Unit =
        if variantToSnCtor != null then callVFTCtor(variantToSnCtor, dest, variant)

    def extractNodePathFromVariant(variant: Ptr[Byte], dest: Ptr[Byte]): Unit =
        if variantToNpCtor != null then callVFTCtor(variantToNpCtor, dest, variant)

    /** Register a custom icon for an extension class. `iconPath` must be a `res://` path. */
    def registerIcon(library: Ptr[Byte], classNameSN: Ptr[Byte], iconPath: String)(using
        Zone
    ): Unit =
        if registerIconFnAddr == null then return
        val strBuf = stackalloc[Byte](8)
        memset(strBuf, 0, 8.toUSize)
        stringNewFn(strBuf, toCString(iconPath))
        type RegisterIconFn = CFuncPtr3[Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]
        val registerIconFn = CFuncPtr.fromPtr[RegisterIconFn](registerIconFnAddr)
        registerIconFn(library, classNameSN, strBuf)
        if strDestructorPtr != null then
            val destructor = CFuncPtr.fromPtr[PtrDestructorFn](strDestructorPtr)
            destructor(strBuf)
    end registerIcon

    // CFuncPtr calls must not be inlined inside Zone{} blocks under -source:future;
    // wrap them in named methods called from Zone context instead.
    private def callVFTCtor(ctor: Ptr[Byte], dest: Ptr[Byte], src: Ptr[Byte]): Unit =
        val fn = CFuncPtr.fromPtr[VFTCtorFn](ctor)
        fn(dest, src)

    private def callPtrDtor(dtor: Ptr[Byte], buf: Ptr[Byte]): Unit =
        val fn = CFuncPtr.fromPtr[PtrDestructorFn](dtor)
        fn(buf)

    private def callNodePathCtor(ctor: Ptr[Byte], dest: Ptr[Byte], args: Ptr[Ptr[Byte]]): Unit =
        val fn = CFuncPtr.fromPtr[NodePathCtorFn](ctor)
        fn(dest, args)

    /** Copy-construct an Array handle. `dest` must be uninitialized (8 bytes); `src` must point to
      * the source Array handle bytes. After this call, `dest` holds a proper refcounted reference.
      */
    private[gdext] def copyArrayHandle(dest: Ptr[Byte], src: Ptr[Byte]): Unit =
        if arrayCopyCtorPtr == null then return
        val args = stackalloc[Ptr[Byte]](1); args(0) = src
        callNodePathCtor(arrayCopyCtorPtr, dest, args)
    end copyArrayHandle

    /** Copy-construct a Dictionary handle. `dest` must be uninitialized (8 bytes); `src` must point
      * to the source Dictionary handle bytes. After this call, `dest` holds a proper refcounted
      * reference.
      */
    private[gdext] def copyDictHandle(dest: Ptr[Byte], src: Ptr[Byte]): Unit =
        if dictCopyCtorPtr == null then return
        val args = stackalloc[Ptr[Byte]](1); args(0) = src
        callNodePathCtor(dictCopyCtorPtr, dest, args)
    end copyDictHandle

    /** Destroy a 24-byte Variant buffer, freeing any internally allocated resources (string
      * buffers, Array/Dict refcounts, etc.). Safe to call on primitive-only Variants (no-op).
      */
    private[gdext] def destroyVariant(buf: Ptr[Byte]): Unit =
        if variantDestroyPtr == null || buf == null then return
        val fn = CFuncPtr.fromPtr[VariantDestroyFn](variantDestroyPtr)
        fn(buf)
    end destroyVariant

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
        if objectCastToFnPtr == null then null else objectCastToFnPtr(obj, classTag)

    /** Return the engine instance ID for an object (0 for null). */
    def objectGetInstanceId(obj: Ptr[Byte]): Long =
        if objectGetInstanceIdFnPtr == null then 0L else objectGetInstanceIdFnPtr(obj)

    /** Destroy a manually-managed (non-RefCounted) object. */
    def objectDestroy(obj: Ptr[Byte]): Unit =
        if objectDestroyFnPtr != null then objectDestroyFnPtr(obj)

    /** Activate an EditorPlugin class that was registered at Editor level. Equivalent to adding it
      * to `editor_plugins` in project.godot.
      */
    def editorAddPlugin(classNameSN: Ptr[Byte]): Unit =
        if editorAddPluginFnPtr != null then editorAddPluginFnPtr(classNameSN)

    /** Deactivate a previously activated EditorPlugin. Must be called before unregistering the
      * class.
      */
    def editorRemovePlugin(classNameSN: Ptr[Byte]): Unit =
        if editorRemovePluginFnPtr != null then editorRemovePluginFnPtr(classNameSN)
end GdxApi
