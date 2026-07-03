# GDExt Generator FFI

The ffi-layer generator: reads `gdextension_interface.json` only and emits the raw C-ABI bindings in [`gdext/ffi`](../../ffi/README.md), plus a few core-layer files into [`gdext/core/src/gdext/core/generated/`](../../core/README.md).

Entry point: `FfiGeneratorMain.scala`. Run via `mill gdext.generator.ffi.generate` (optionally `--version 4.7.0`).

## Generators (`src/gdext/generator/ffi/`)

- `TypeMapper.scala` — maps GDExtension JSON type strings to Scala Native types; shared by the other generators
- `FfiGenerator.scala` — emits `GdxFfi.scala` (function pointer vars + `initialize()`)
- `SysTypesGenerator.scala` — emits `GdExtTypes.scala` (C-ABI type aliases, enums)
- `SysInterfaceGenerator.scala` — emits `GdExtInterface.scala` (`CFuncPtr` aliases per interface function)
- `CStructExtGenerator.scala` — emits `CStruct23`–`CStruct26` and related raw struct layouts
- `HeapBuiltinGenerator.scala`, `PackedArraysGenerator.scala`, `StringNamesGenerator.scala` — emit *core-layer* files (need `gdext.core` types, so they're written to `gdext/core/src/gdext/core/generated/` instead of the ffi-layer output dir, even though they still declare `package com.`julian-avar`.gdext.core`)

## Output split

`FfiGeneratorMain` writes two sets of files from one run, to two explicit output directories passed on the command line:

- **Ffi files** (`FfiGenerator`, `SysTypesGenerator`, `SysInterfaceGenerator`, `CStructExtGenerator`) → `gdext/ffi/src/` — no `gdext.core` dependency, package `gdext.ffi`
- **Core files** (`HeapBuiltinGenerator`, `PackedArraysGenerator`, `StringNamesGenerator`) → `gdext/core/src/gdext/core/generated/` — free to use `gdext.core` types, package `gdext.core`. They live in a `generated/` subfolder of `core/src` purely for physical separation from hand-written core code; same module, same package, same compile unit.

See [Two ownership systems](../../../docs/01-two-ownership-systems.md) and the architecture notes in [`gdext/core/README.md`](../../core/README.md) for why the split exists.

## See also

[`gdext/generator/README.md`](../README.md) for the api-layer generator that depends on this one's output.
