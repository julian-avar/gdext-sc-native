package gdext

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

// ── Function pointer types ──────────────────────────────────────────────────

type GetProcAddressFn = CFuncPtr1[CString, Ptr[Byte]]
type StringNameNewFn  = CFuncPtr2[Ptr[Byte], CString, Unit]
// type ConstructObjectFn = CFuncPtr1[Ptr[Byte], Ptr[Byte]]
type RegisterClass2Fn = CFuncPtr4[Ptr[Byte], Ptr[Byte], Ptr[Byte], Ptr[ClassCreationInfo2], Unit]
type CreateInstanceFn = CFuncPtr1[Ptr[Byte], Ptr[Byte]]
type FreeInstanceFn   = CFuncPtr2[Ptr[Byte], Ptr[Byte], Unit]
type GdxInitCallback  = CFuncPtr2[Ptr[Byte], CInt, Unit]
// p_instance, p_args, r_ret
type CallVirtualFn = CFuncPtr3[Ptr[Byte], Ptr[Ptr[Byte]], Ptr[Byte], Unit]
// p_userdata, p_name (StringName*) → GDExtensionClassCallVirtual (or null)
type GetVirtualFn = CFuncPtr2[Ptr[Byte], Ptr[Byte], Ptr[Byte]]

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

val StringNameSize: CSize = 8.toUSize
