# GDExt API

The api-layer: generated, idiomatic engine bindings merged with the hand-written user-facing
facade. This is the module game code actually depends on (via the top-level `gdext` aggregate).
Like `ffi` and `core`, it's a `Cross.Module[String]` built once per supported Godot version.

Depends on [`gdext/ffi`](../ffi/README.md) and [`gdext/core`](../core/README.md).

## Generated content

Produced by the `APIGeneratorModule` trait (see
[`generator-module-mill-plugin`](../generator-module-mill-plugin/README.md)) as a `generatedSources`
Mill task on every compile, reading both `gdextension_interface.json` and `extension_api.json`:
engine class wrappers (`classes/`), virtual dispatch tables (`virtuals/`), builtin value types
(`GodotBuiltins.scala`), the FFI handshake (`Interface.scala`), utility functions
(`UtilityFunctions.scala`), and global enums/scope (`GlobalScope.scala`). Nothing generated is
checked into `src/`.

## Hand-written content (`src/com/julian-avar/gdext/api/`)

- `api.scala` — the `gdext.api.*` re-export surface every `@gdclass` game file imports:
  annotations (`@gdclass`, `@gdexport`, `@func`, `@signal`, `@onready`, ...), `Register`,
  `Gd[T]`/`GodotObject`, variant typeclasses, signal handles, `CallableLambda`, and the
  low-level escape hatch
- `GodotEntry.scala` — thin wrapper around `gdext.core.GodotEntry.init`; the entry point game
  projects call from their `@exported("godot_scala_init")` function
- `lowlevel.scala` — the sanctioned escape hatch for library/plugin authors who need raw
  `GdxFfi`/`GdxApi` access instead of the annotation-driven facade
- `editor/ScalaEditorPlugin.scala` — base class for `@tool`-registered `EditorPlugin` subclasses
- `godot/Predef.scala` — `println`/`printerr`/`nullOf`/`Log` and other output/DX helpers
- `scala/ScalaScript.scala`, `scala/ScalaScriptLanguage.scala` — the `ScalaScriptLanguage`
  integration that lets `.scala` files be attached directly as Godot scripts

## See also

- [`gdext/README.md`](../README.md) — build pipeline and module map
- [`docs/00-architecture-overview.md`](../../docs/00-architecture-overview.md) — data-flow diagrams
- [`FEATURES.md`](../../FEATURES.md#known-limitations) — known limitations that live in this layer (unwired `@async`/`@rpc`/`@globalClass`, missing builtin convenience methods, ScalaDoc forwarding)
