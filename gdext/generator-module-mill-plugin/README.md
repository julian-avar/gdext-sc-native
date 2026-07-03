# GDExt Generator

The api-layer generator: reads both `gdextension_interface.json` and `extension_api.json` and emits the idiomatic, game-dev-facing bindings in [`gdext/generated`](../generated/README.md).

Entry point: `GeneratorMain.scala`. Run via `mill gdext.generator.generate` (optionally `--version 4.7.0`); formats output with scalafmt afterward if `.scalafmt.conf` is present.

## Layout

- `parser/` — `Ast.scala` (parsed model: classes, builtins, enums, utility functions) and `Parser.scala` (turns the raw API JSON into that model)
- `trees/` — one generator per output kind, each producing scalameta trees:
  - `TypesGenerator.scala` — value types (`types/`)
  - `InterfacesGenerator.scala` — `Interface.scala`
  - `BuiltinsGenerator.scala` — `GodotBuiltins.scala`
  - `VirtualsGenerator.scala` — `virtuals/*Virtuals.scala`
  - `WrappersGenerator.scala` — `classes/*.scala` (engine class wrappers, including `lazy val` method binds)
  - `UtilitiesGenerator.scala` — `UtilityFunctions.scala`
  - `GlobalScopeGenerator.scala` — `GlobalScope.scala`
  - `util.scala` — shared tree-building helpers
- `Generator.scala` — orchestrates the above into the file list `GeneratorMain` writes out
- `resources/` — per-Godot-version `gdextension_interface.json` / `extension_api.json` inputs, plus `api/` source files fetched via the `downloadApi` task

## Relationship to the ffi generator

This generator depends on `gdext.core` (it generates code that uses `gdext.core` types) and on the ffi-layer output already produced by [`gdext/generator/ffi`](ffi/README.md) — see [Two-layer generated code](../../docs/01-two-ownership-systems.md) for the full split between ffi (`gdext.ffi`) and api (`gdext.generated`) layers.

Do not hand-edit anything under `gdext/generated`; change a generator here instead and re-run `generate`.
