package gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import gdext.core.types.{CStruct23, Tags as ExtraStructTags}

// ── Function pointer types ──────────────────────────────────────────────────

type GetProcAddressFn = CFuncPtr1[CString, Ptr[Byte]]
type StringNameNewFn  = CFuncPtr2[Ptr[Byte], CString, Unit]
// type ConstructObjectFn = CFuncPtr1[Ptr[Byte], Ptr[Byte]]
type RegisterClass2Fn = CFuncPtr4[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[ClassCreationInfo2], Unit]
// info is passed as raw Ptr[Byte] — struct filled by byte offset in ClassRegistrar
type RegisterClass3Fn = CFuncPtr4[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]
// (classUserdata, godotObjectPtr) → instancePtr; called on hot-reload to re-bind instances
type RecreateInstanceFn = CFuncPtr2[Ptr[Byte], Ptr[Byte], Ptr[Byte]]
// classdb_get_class_tag(classNameSN) → Ptr (non-null = class is registered in ClassDB)
type GetClassTagFn = CFuncPtr1[Ptr[Byte], Ptr[Byte]]
type CreateInstanceFn = CFuncPtr1[Ptr[Byte], Ptr[Byte]]
type FreeInstanceFn   = CFuncPtr2[Ptr[Byte], Ptr[Byte], Unit]
type GdxInitCallback  = CFuncPtr2[Ptr[Byte], CInt, Unit]
// p_instance, p_args, r_ret
type CallVirtualFn = CFuncPtr3[Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], Unit]
// p_userdata, p_name (StringName*) → GDExtensionClassCallVirtual (or null)
type GetVirtualFn = CFuncPtr2[Ptr[Byte], Ptr[Byte], Ptr[Byte]]
// (p_userdata, p_name) → call_data ptr (returned to call_virtual_with_data_func)
type GetVirtualCallDataFn = CFuncPtr2[Ptr[Byte], Ptr[Byte], Ptr[Byte]]
// (p_instance, p_name, p_call_data, p_args, r_ret)
type CallVirtualWithDataFn =
    CFuncPtr5[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], Unit]

// ── GDExtensionInitialization ───────────────────────────────────────────────
// C layout (x86_64):
//   +0  int32   minimum_initialization_level
//   +4  [4 pad]
//   +8  void*   userdata
//   +16 void*   initialize   (GdxInitCallback)
//   +24 void*   deinitialize (GdxInitCallback)

type GdxInitStruct = CStruct4[CInt, Ptr[Byte], Ptr[Byte], Ptr[Byte]]

object GdxInitLevel:
    val Core    = 0
    val Servers = 1
    val Scene   = 2
    val Editor  = 3
end GdxInitLevel

// ── GDExtensionClassCreationInfo2 ──────────────────────────────────────────
// 22 fields: 3 × UByte then 19 function-pointer/void* fields.
// Fields 15 and 16 are typed as CFuncPtr so the compiler stores the native
// function pointer rather than the GC object reference.

type ClassCreationInfo2 = CStruct22[
  UByte,     // 1  is_virtual
  UByte,     // 2  is_abstract
  UByte,     // 3  is_exposed
  Ptr[Byte], // 4  set_func
  Ptr[Byte], // 5  get_func
  Ptr[Byte], // 6  get_property_list_func
  Ptr[Byte], // 7  free_property_list_func
  Ptr[Byte], // 8  property_can_revert_func
  Ptr[Byte], // 9  property_get_revert_func
  Ptr[Byte], // 10 validate_property_func
  Ptr[Byte], // 11 notification_func
  Ptr[Byte], // 12 to_string_func
  Ptr[Byte], // 13 reference_func
  Ptr[Byte], // 14 unreference_func
  Ptr[Byte], // 15 create_instance_func ← required
  Ptr[Byte], // 16 free_instance_func   ← required
  Ptr[Byte], // 17 recreate_instance_func
  Ptr[Byte], // 18 get_virtual_func
  Ptr[Byte], // 19 get_virtual_call_data_func
  Ptr[Byte], // 20 call_virtual_with_data_func
  Ptr[Byte], // 21 get_rid_func
  Ptr[Byte]  // 22 class_userdata
]

given Tag[ClassCreationInfo2] = Tag
    .materializeCStruct22Tag[
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

// ── GDExtensionClassCreationInfo3 (Godot 4.3+) ────────────────────────────
// Adds `is_runtime` at field 4 (all subsequent fields shift by one).
// Function pointer signatures are unchanged vs info2.
// Used with classdb_register_extension_class3.

type ClassCreationInfo3 = CStruct23[
  UByte,     // 1  is_virtual
  UByte,     // 2  is_abstract
  UByte,     // 3  is_exposed
  UByte,     // 4  is_runtime          ← NEW: 1 for normal game scripts
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
  Ptr[Byte], // 16 create_instance_func ← required
  Ptr[Byte], // 17 free_instance_func   ← required
  Ptr[Byte], // 18 recreate_instance_func ← for hot-reload
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

// ── GDExtensionPropertyInfo ────────────────────────────────────────────────
// C layout (x86_64):
//   +0  uint32  type          (GDExtensionVariantType)
//   +4  [4 pad]
//   +8  void*   name          (StringName*)
//   +16 void*   class_name    (StringName*)
//   +24 uint32  hint
//   +28 [4 pad]
//   +32 void*   hint_string   (String*)
//   +40 uint32  usage
//   +44 [4 pad] (struct total: 48 bytes)

type PropertyInfo =
    CStruct6[CUnsignedInt, Ptr[Byte], Ptr[Byte], CUnsignedInt, Ptr[Byte], CUnsignedInt]

given Tag[PropertyInfo] = Tag
    .materializeCStruct6Tag[CUnsignedInt, Ptr[Byte], Ptr[Byte], CUnsignedInt, Ptr[
      Byte
    ], CUnsignedInt]

// (instance, name, value/ret) → GDExtensionBool
type PropertyCallbackFn = CFuncPtr3[Ptr[Byte], Ptr[Byte], Ptr[Byte], UByte]
// (library, class_name, info, setter_name, getter_name)
type RegisterPropertyFn = CFuncPtr5[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]

// ── GDExtensionClassMethodInfo ─────────────────────────────────────────────
// C layout (x86_64):
//   +0  Ptr[Byte]     name
//   +8  Ptr[Byte]     method_userdata
//   +16 Ptr[Byte]     call_func
//   +24 Ptr[Byte]     ptrcall_func
//   +32 uint32        method_flags
//   +36 uint8         has_return_value
//   +40 Ptr[Byte]     return_value_info  (3 bytes padding at +37)
//   +48 uint32        return_value_metadata
//   +52 uint32        argument_count
//   +56 Ptr[Byte]     arguments_info
//   +64 Ptr[Byte]     arguments_metadata
//   +72 uint32        default_argument_count
//   +80 Ptr[Byte]     default_arguments  (4 bytes padding at +76)
//   Total: 88 bytes

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

// (method_userdata, p_instance, p_args, p_arg_count, r_return, r_error)
type MethodCallFn =
    CFuncPtr6[Ptr[Byte], Ptr[Byte], Ptr[Ptr[Byte]], Long, Ptr[Byte], Ptr[Byte], Unit]
// (library, class_name, method_info)
type RegisterMethodFn = CFuncPtr3[Ptr[Byte], Ptr[Byte], Ptr[Byte], Unit]
