# GDExt Generator Module (Mill plugin)

A library of Mill traits — not a standalone CLI tool — that supply the `generatedSources` task
each cross-version `gdext.ffi`/`gdext.core`/`gdext.api` module mixes in. Package:
`com.\`julianavar\`.gdext.godotscalanativelib`.

- **`GeneratorModule`** (`GeneratorModule.scala`) — shared base trait: resolves per-version
  resource paths, asserts required `gdextension_interface.json`/`extension_api.json` exist,
  exposes `downloadGeneratorResources`/`dumpApi`/`diffInterface` utility tasks, and
  `writeFiles(files, root)` for turning a `Vector[ScalaFile]` into files under a Mill `Task.dest`.
- **`FFIGeneratorModule`** (`FFIGeneratorModule.scala`) — mixed into `gdext.ffi`. Reads
  `gdextension_interface.json` only and emits the raw C-ABI surface (see
  [`ffi/README.md`](src/com/julianavar/gdext/godotscalanativelib/ffi/README.md)).
- **`CoreGeneratorModule`** (`CoreGeneratorModule.scala`) — mixed into `gdext.core`. Emits the
  core-layer generated files (heap builtins, packed arrays, string names) that need `gdext.core`
  types.
- **`APIGeneratorModule`** (`APIGeneratorModule.scala`) — mixed into `gdext.api`. Reads both API
  JSONs and emits the idiomatic, game-dev-facing bindings in
  [`gdext/api`](../api/README.md): `classes/`, `virtuals/`, `types/`, builtins, utility functions,
  global scope.

Each trait overrides `generatedSources` as an ordinary Mill `Task` — there is no `generate`
command to run by hand; the sources are produced fresh into Mill's `out/` directory whenever the
owning module compiles, once per Godot version in `Config.godotVersions`.

## Layout

- `resource_parser/` — `Ast.scala` (parsed model: classes, builtins, enums, utility functions) and `Parser.scala` (turns the raw API JSON into that model)
- `ffi/generators/`, `core/generators/`, `api/generators/` — one generator per output kind, each producing scalameta trees (see the per-layer READMEs)
- `ffi/Generator.scala`, `core/Generator.scala`, `api/Generator.scala` — orchestrate the generators above into the `Vector[ScalaFile]` each `*GeneratorModule.generatedSources` task writes out
- `utils.scala` — shared tree-building helpers (`ScalaFile`, etc.)
- `resources/` — per-Godot-version `gdextension_interface.json` / `extension_api.json` inputs, plus `api/` source files fetched via `downloadGeneratorResources`

## Relationship between the three generator traits

`APIGeneratorModule` depends on `gdext.core` (it generates code that uses `gdext.core` types) and
on the ffi-layer output already produced by `gdext.ffi` via `FFIGeneratorModule` — see
[Two-layer generated code](../../docs/01-two-ownership-systems.md) for the full split between ffi
(`gdext.ffi`), core (`gdext.core`), and api (`gdext.api`) layers.

Do not hand-edit anything these traits produce; change a generator here instead — it reruns on the
next compile automatically.
