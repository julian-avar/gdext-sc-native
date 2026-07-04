package net.`julian-avar`.gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import net.`julian-avar`.gdext.ffi.{CStruct23, Tags as ExtraStructTags}
import net.`julian-avar`.gdext.ffi.{GDExtensionClassCreationInfo2, GDExtensionPropertyInfo}

// ── Scala-friendly aliases
// ClassCreationInfo2 and PropertyInfo are now derived from the generated
// gdext.ffi types. Tags are re-provided here so gdext.core code
// doesn't need to import gdext.ffi explicitly.

type ClassCreationInfo2 = GDExtensionClassCreationInfo2
given Tag[ClassCreationInfo2] =
    import net.`julian-avar`.gdext.ffi.given
    summon[Tag[GDExtensionClassCreationInfo2]]

type PropertyInfo = GDExtensionPropertyInfo
given Tag[PropertyInfo] =
    import net.`julian-avar`.gdext.ffi.given
    summon[Tag[GDExtensionPropertyInfo]]

// ── Raw handle wrappers ─────────────────────────────────────────────────────
// RID and Callable are opaque 8/16-byte Godot handles with no members to
// reflect; ToVariant/FromVariant/PtrArg/PtrRet instances live in
// VariantTypeclasses.scala/Ptrcall.scala and operate on `.ptr` directly.

final class RID(val ptr: Ptr[Byte])

final class Callable(val ptr: Ptr[Byte])

// ── Function pointer types ──────────────────────────────────────────────────

type GetProcAddressFn = CFuncPtr1[CString, Ptr[Byte]]
type StringNameNewFn  = CFuncPtr2[Ptr[Byte], CString, Unit]
type RegisterClass2Fn = CFuncPtr4[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[ClassCreationInfo2], Unit]
// info is passed as raw Ptr[Byte] — struct filled by byte offset in ClassRegistrar
type RegisterClass3Fn = CFuncPtr4[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]
// (classUserdata, godotObjectPtr) → instancePtr; called on hot-reload to re-bind instances
type RecreateInstanceFn    = CFuncPtr2[Ptr[Byte], Ptr[Byte], Ptr[Byte]]
type GetClassTagFn         = CFuncPtr1[Ptr[Byte], Ptr[Byte]]
type CreateInstanceFn      = CFuncPtr1[Ptr[Byte], Ptr[Byte]]
type FreeInstanceFn        = CFuncPtr2[Ptr[Byte], Ptr[Byte], Unit]
type GdxInitCallback       = CFuncPtr2[Ptr[Byte], CInt, Unit]
type CallVirtualFn         = CFuncPtr3[Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], Unit]
type GetVirtualFn          = CFuncPtr2[Ptr[Byte], Ptr[Byte], Ptr[Byte]]
type GetVirtualCallDataFn  = CFuncPtr2[Ptr[Byte], Ptr[Byte], Ptr[Byte]]
type CallVirtualWithDataFn =
    CFuncPtr5[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], Unit]

// ── GDExtensionInitialization ───────────────────────────────────────────────
// Kept hand-written: field 1 is CInt here but GDExtensionInitializationLevel
// (CUnsignedInt) in the generated type; GdxInitCallback is specific CFuncPtr.
// C layout (x86_64): +0 int32 level, +4 pad, +8 void*, +16 void*, +24 void*

type GdxInitStruct = CStruct4[CInt, Ptr[Byte], Ptr[Byte], Ptr[Byte]]

given Tag[GdxInitStruct] = Tag.materializeCStruct4Tag[CInt, Ptr[Byte], Ptr[Byte], Ptr[Byte]]

object GdxInitLevel:
    val Core    = 0
    val Servers = 1
    val Scene   = 2
    val Editor  = 3
end GdxInitLevel

// ── GDExtensionClassCreationInfo3 (Godot 4.3+, 23 fields) ─────────────────
// Kept hand-written: 23 fields exceeds the CStruct22 standard limit.
// Fields filled by raw byte offset in ClassRegistrar — no field accessors used.

type ClassCreationInfo3 = CStruct23[
  UByte,     // 1  is_virtual
  UByte,     // 2  is_abstract
  UByte,     // 3  is_exposed
  UByte,     // 4  is_runtime
  Ptr[Byte], // 5  set_func
  Ptr[Byte], // 6  get_func
  Ptr[Byte], // 7  get_property_list_func
  Ptr[Byte], // 8  free_property_list_func
  Ptr[Byte], // 9  property_can_revert_func
  Ptr[Byte], // 10 property_get_revert_func
  Ptr[Byte], // 11 validate_property_func
  Ptr[Byte], // 12 notification_func
  Ptr[Byte], // 13 to_string_func
  Ptr[Byte], // 14 reference_func
  Ptr[Byte], // 15 unreference_func
  Ptr[Byte], // 16 create_instance_func
  Ptr[Byte], // 17 free_instance_func
  Ptr[Byte], // 18 recreate_instance_func
  Ptr[Byte], // 19 get_virtual_func
  Ptr[Byte], // 20 get_virtual_call_data_func
  Ptr[Byte], // 21 call_virtual_with_data_func
  Ptr[Byte], // 22 get_rid_func
  Ptr[Byte]  // 23 class_userdata
]

given Tag[ClassCreationInfo3] = ExtraStructTags
    .materializeCStruct23Tag[
      UByte,
      UByte,
      UByte,
      UByte,
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte],
      Ptr[Byte]
    ]

val StringNameSize: CSize = 8.toUSize

// ── GDExtensionClassMethodInfo (13 fields) ─────────────────────────────────
// Kept hand-written: fields 3 + 4 are specific CFuncPtr types in the generated
// version but must accept raw Ptr[Byte] assignments in GdxApi without casting.
// C layout (x86_64): see comments below; total 88 bytes.

type ClassMethodInfo = CStruct13[
  Ptr[Byte],    // 1  name
  Ptr[Byte],    // 2  method_userdata
  Ptr[Byte],    // 3  call_func
  Ptr[Byte],    // 4  ptrcall_func
  CUnsignedInt, // 5  method_flags
  UByte,        // 6  has_return_value
  Ptr[Byte],    // 7  return_value_info
  CUnsignedInt, // 8  return_value_metadata
  CUnsignedInt, // 9  argument_count
  Ptr[Byte],    // 10 arguments_info
  Ptr[Byte],    // 11 arguments_metadata
  CUnsignedInt, // 12 default_argument_count
  Ptr[Byte]     // 13 default_arguments
]

given Tag[ClassMethodInfo] = Tag
    .materializeCStruct13Tag[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte], CUnsignedInt, UByte, Ptr[
      Byte
    ], CUnsignedInt, CUnsignedInt, Ptr[Byte], Ptr[Byte], CUnsignedInt, Ptr[Byte]]

// (instance, name, value/ret) → GDExtensionBool
type PropertyCallbackFn = CFuncPtr3[Ptr[Byte], Ptr[Byte], Ptr[Byte], UByte]
// (library, class_name, info, setter_name, getter_name)
type RegisterPropertyFn = CFuncPtr5[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]

// (method_userdata, p_instance, p_args, p_arg_count, r_return, r_error)
type MethodCallFn =
    CFuncPtr6[Ptr[Byte], Ptr[Byte], Ptr[Ptr[Byte]], Long, Ptr[Byte], Ptr[Byte], Unit]
// (library, class_name, method_info)
type RegisterMethodFn = CFuncPtr3[Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]
