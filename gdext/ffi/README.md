# GDExt FFI

The raw C-ABI surface of GDExtension, with no dependency on `gdext.core`. A sibling module of
`gdext.core` — depended on *by* `core`, not nested inside it (mirrors godot-rust's `godot-ffi`
crate being a sibling of `godot-core`). Like `core` and `api`, it's a `Cross.Module[String]` built
once per supported Godot version.

Entirely generated: `gdext.ffi` mixes in the `FFIGeneratorModule` trait, which reads
`gdextension_interface.json` only and produces these sources as a `generatedSources` Mill task on
every compile — nothing under this module is checked into `src/`. See
[`gdext/generator-module-mill-plugin/.../ffi/README.md`](../generator-module-mill-plugin/src/com/julian-avar/gdext/godotscalanativelib/ffi/README.md).

## Contents

- `GdxFfi.scala` — raw `Ptr[Byte]` function pointer vars and `initialize()` for binding the extension interface
- `GdExtTypes.scala` — C-ABI type aliases (`GDExtensionInt`, `GDExtensionVariantType`, enums, etc.)
- `GdExtInterface.scala` — `CFuncPtr` type aliases, one per interface function (`GDExtensionInterfaceVariantNewCopy`, etc.)
- `CStruct23.scala`–`CStruct26.scala` — raw struct layouts for builtin value types and engine-side fixed-size structs

## Style

C names and shapes are preserved as closely as Scala Native allows: raw pointers, `extern`/`CFuncPtr` types, `private[gdext]` visibility where the symbol shouldn't leak past this module. Nothing here is meant to be idiomatic or user-facing — that's [`gdext/api`](../api/README.md).

Do not hand-edit these files; they're regenerated on every compile. See [`gdext/core/README.md`](../core/README.md) for how this layer is consumed.
