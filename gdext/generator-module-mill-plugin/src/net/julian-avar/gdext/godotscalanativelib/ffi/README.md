# GDExt Generator FFI

The ffi-layer generators: read `gdextension_interface.json` only and produce the raw C-ABI
bindings compiled into [`gdext/ffi`](../../../../../../../ffi/README.md). Wired up via the
`FFIGeneratorModule` trait (`../FFIGeneratorModule.scala`), which `gdext.ffi` mixes in as its
`generatedSources` task — there is no CLI entry point to run by hand; it reruns on every compile.

Note: the core-layer files that used to be generated from this same JSON (heap builtins, packed
arrays, string names) are now produced by `CoreGeneratorModule`/`core/generators/` instead, since
they need `gdext.core` types — see [`CoreGeneratorModule.scala`](../CoreGeneratorModule.scala)
and [`gdext/core/README.md`](../../../../../../../core/README.md).

## Generators (`ffi/generators/`)

- `TypeMapper.scala` — maps GDExtension JSON type strings to Scala Native types; shared by the other generators
- `GdxFfiGenerator.scala` — emits `GdxFfi.scala` (function pointer vars + `initialize()`)
- `TypesGenerator.scala` — emits `GdExtTypes.scala` (C-ABI type aliases, enums)
- `InterfaceGenerator.scala` — emits `GdExtInterface.scala` (`CFuncPtr` aliases per interface function)
- `CStructExtGenerator.scala` — emits `CStruct23`–`CStruct26` and related raw struct layouts

`ffi/Generator.scala` orchestrates these into the `Vector[ScalaFile]` that `FFIGeneratorModule`
writes into Mill's `out/` task directory for the module's target Godot version.

## See also

[`gdext/generator-module-mill-plugin/README.md`](../../../../../../README.md) for how this fits alongside the core- and api-layer generators.
