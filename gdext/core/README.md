# GDExt Core

The core-layer runtime: hand-written Scala that sits directly on top of the FFI bindings in [`gdext/ffi`](../ffi/README.md), and that the api-layer generated code in [`gdext/generated`](../generated/README.md) builds on. `gdext.core` depends on `gdext.ffi` as a sibling module — it doesn't contain it.

This is where GDExtension's C-ABI is turned into a usable, GC-friendly Scala runtime — class registration, variant/refcounting machinery, the Zone allocation pattern, and the typeclasses generated code relies on.

## Contents (`src/gdext/core/`)

- **Lifecycle & registration** — `GodotEntry.scala`, `ClassRegistrar.scala`, `GdClassRegistry.scala`, `Register.scala`, `annotations.scala`: extension entry point, `@gdclass`/`@gdexport` machinery, class/method/signal registration with the engine
- **Object model** — `Gd.scala`, `GodotObject.scala`, `GodotClass.scala`: the `Gd[T]` handle type and base wrapper types for engine objects
- **Variants & marshalling** — `GodotTypes.scala`, `VariantTypeclasses.scala`, `Ptrcall.scala`, `DefaultValue.scala`: `ToVariant`/`FromVariant` typeclasses and ptrcall argument/return marshalling
- **Collections & strings** — `GdArray.scala`, `GdDict.scala`
- **Callables & signals** — `Callables.scala`, `SignalDescriptor.scala`, `SignalHandles.scala`
- **Properties/exports** — `PropertyDescriptor.scala`, `ExportType.scala`, `ExportHint.scala`, `ExportWrappers.scala`
- **Infra** — `GdxApi.scala` (typed wrapper over the FFI), `FileLogger.scala`, `Log.scala`
- `method/`, `types/`, `virtual/`, `v47/` — supporting submodules grouped by concern

## Generated code in this module

`gdext.ffi` (see its own README) is a separate module this one depends on — it is not part of `core`'s sources. Within `core` itself, `FfiGeneratorMain` emits a handful of *core-layer* files into `src/gdext/core/generated/` (`PackedArrays.scala`, `StringName.scala`, `StringNames.scala`, `NodePath.scala` — heap builtins, packed arrays, string name constants) because they need access to `gdext.core` types that `gdext.ffi` can't depend on. They share `package com.`julian-avar`.gdext.core` with the hand-written code around them; the `generated/` subfolder exists purely so it's visually obvious which files are generator output. See `outDirCore` in `FfiGeneratorMain.scala`.

## See also

- [Two ownership systems](../../docs/01-two-ownership-systems.md), [Gd lifecycle](../../docs/02-gd-lifecycle.md), [Variant lifecycle](../../docs/03-variant-lifecycle.md), [Zone system](../../docs/05-zone-system.md) for the concepts implemented here
- [`gdext/generated`](../generated/README.md) for the idiomatic, generated api layer that depends on this module
