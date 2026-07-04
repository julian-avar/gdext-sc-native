# gdext

Godot 4.x GDExtension binding for Scala Native. This directory holds the binding: three
cross-version Scala modules (`ffi`, `core`, `api`), the generator traits that feed them, and the
published Mill plugin artifact. See [`docs/00-architecture-overview.md`](../docs/00-architecture-overview.md)
for the module map and data-flow diagrams; this file covers the build pipeline and per-module known
issues.

## Build/compile pipeline

`ffi`, `core`, and `api` are each a `Cross.Module[String]`, built once per supported Godot version
(`4.5.0`, `4.6.1`, `4.7.0` — see `Config.godotVersions` in `mill-build/src/utils.scala`). Codegen is
not a separate step: each cross module mixes in a generator trait from
[`generator-module-mill-plugin`](generator-module-mill-plugin/README.md) that overrides Mill's
`generatedSources` task, so the generated sources are (re)produced automatically whenever that
module is compiled, straight into Mill's `out/` task directory — nothing generated is checked into
`src/`.

```
gdext.ffi(<version>)   FFIGeneratorModule.generatedSources reads gdextension_interface.json
                        → compiles the raw C-ABI surface

gdext.core(<version>)  CoreGeneratorModule.generatedSources emits the core-layer generated files
                        (heap builtins, packed arrays, string names)
                        → compiles, depends on gdext.ffi(<version>)

gdext.api(<version>)   APIGeneratorModule.generatedSources reads both API JSONs, emits the
                        idiomatic engine-class wrappers, virtuals, builtins, utility functions
                        → compiles, depends on gdext.ffi(<version>) + gdext.core(<version>)

gdext(<version>)       aggregates api + core into the single artifact consumers depend on
```

Bumping to a new Godot version means adding its resources under
[`generator-module-mill-plugin/resources/`](generator-module-mill-plugin/resources) and adding the
version string to `Config.godotVersions` — no manual "run the generator" step. `mill-plugin` is the
separately published plugin artifact (`gdext-mill-plugin`) that `examples/*` and downstream
projects actually depend on; see [[project-example-builds]] / `just publishLocal`.

## Modules

### [`generator-module-mill-plugin`](generator-module-mill-plugin/README.md) — generator traits

Not standalone tools anymore — a library of Mill traits (`FFIGeneratorModule`,
`CoreGeneratorModule`, `APIGeneratorModule`, all extending the shared `GeneratorModule`) mixed
directly into `gdext.ffi`/`gdext.core`/`gdext.api`. Reads `gdextension_interface.json` and
`extension_api.json` per Godot version and emits Scala source trees at compile time.
**Known issues:** none generator-specific currently tracked; see `core`'s `GdxApiV47` gap below,
which this generator does not yet address (no per-version interface codegen).

### [`ffi`](ffi/README.md) — raw C-ABI bindings

Generated, zero-dependency C-ABI surface (`GdxFfi`, `GdExtTypes`, `GdExtInterface`, `CStruct*`).
Depended on by `core`; nothing else should need it directly. Mirrors godot-rust's `godot-ffi` crate
being a sibling of `godot-core` rather than nested inside it.

### [`core`](core/README.md) — runtime

Hand-written Scala on top of [`ffi`](ffi/README.md)'s C-ABI bindings: class registration, `Gd[T]`,
variant marshalling, `GdArray`/`GdDict`, signals, Zones. Everything else in the binding is built on
this. A handful of files that need `gdext.core` types but are otherwise mechanically generated
(heap builtin handles, packed array extensions, the string-name cache) are emitted by
`CoreGeneratorModule` alongside the hand-written sources in this module — same package, produced
at compile time rather than checked in.
**Known issues** (from [`FEATURES.md`](../FEATURES.md#known-limitations)):
- `GdArray`/`GdDict` don't manage the internal Godot Array/Dictionary refcount — call `.destroy()` on locals, or treat exported collections as extension-lifetime handles.
- `FromVariant` for `GdArray`/`GdDict` heap-allocates 8 bytes per read (bounded leak).
- `GdxApiV47` (`core/src/com/julianavar/gdext/core/v47/GdxApiV47.scala`) is a stub — version-specific API loading for post-4.7 features isn't implemented; icon registration works via a hardcoded pointer in `GdxApi.initialize` instead.
- Signal arity is capped at `Signal0`–`Signal8`.

### [`api`](api/README.md) — generated api layer + facade

Idiomatic, game-dev-facing bindings produced by `APIGeneratorModule` (`classes/`, `virtuals/`,
`types/`, builtins, utility functions, global scope) merged with the hand-written facade previously
kept at the top-level `gdext` module: `GodotEntry`, `lowlevel`, `editor/` (editor plugin base), and
`scala/` (the `ScalaScript` language integration so `.scala` files can be used as Godot scripts
directly). Depends on `ffi` + `core`.
**Known issues:** named-constant/convenience methods on some builtin value types are still being
filled in (see roadmap in `FEATURES.md`); `@async`/`@await`, `@rpc`, and `@globalClass` annotations
are defined but not yet wired through codegen; ScalaDoc from `extension_api.json` is not yet
forwarded to generated wrappers; class registration order in `GeneratedRegistrations.scala` depends
on scan order, so a user `Child extends Parent` requires `Parent` to be scanned first.

### `gdext` (top-level module) and `mill-plugin`

`gdext(<version>)` aggregates `api` + `core` (which pulls in `ffi` transitively) into the single
artifact examples and user projects consume. `mill-plugin` (artifact `gdext-mill-plugin`) is the
separately published Mill plugin module examples add to their own `build.mill` to get the
`GodotScalaNativeModule` trait (generated registration/entry-point scanning, `buildExtension`,
etc.) — see `godotscalanativelib/GodotScalaNativeModule.scala`.

## See also

- [`docs/00-architecture-overview.md`](../docs/00-architecture-overview.md) — module-to-Rust-analogue map and data-flow diagrams (build-time codegen, runtime call flow, virtual dispatch)
- [`FEATURES.md`](../FEATURES.md) — full feature list, known limitations, and roadmap (canonical; the per-module lists above are a categorized excerpt and may drift — check there for the current full picture)
- [`docs/`](../docs/) — architecture notes on ownership, lifecycle, Zones, and code generation referenced throughout the module READMEs
