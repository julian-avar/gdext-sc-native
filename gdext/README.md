# gdext

Godot 4.x GDExtension binding for Scala Native. This directory holds the binding's build pipeline: two codegen tools and the four Scala modules they feed. See [`docs/00-architecture-overview.md`](../docs/00-architecture-overview.md) for the module map and data-flow diagrams; this file covers the build pipeline and per-module known issues.

## Build/compile pipeline
```
1. generator/ffi     compiles, then `mill gdext.generator.ffi.generate` runs
                      → writes gdext/ffi/src/  and  gdext/core/src/gdext/core/generated/
2. ffi                compiles
3. core               compiles  (depends on ffi)
4. generator          compiles, then `mill gdext.generator.generate` runs
                      → writes gdext/generated/src/
5. generated          compiles  (depends on core)
6. gdext (top)        compiles  (depends on ffi + core + generated)
```

Re-running step 1 or 4 after a Godot version bump or generator change is required before the corresponding Scala module will reflect the change.

## Modules

### [`generator/ffi`](generator/ffi/README.md) — ffi generator

Standalone Mill tool. Reads only `gdextension_interface.json`; no dependency on any other `gdext` module. Emits the raw C-ABI surface (`GdxFfi`, `GdExtTypes`, `GdExtInterface`, `CStruct*`) into [`gdext/ffi`](ffi/README.md), plus a few core-layer files that need `gdext.core` types (heap builtins, packed arrays, string names) into `gdext/core/src/gdext/core/generated/`.
**Known issues:** none generator-specific currently tracked; see `core`'s `GdxApiV47` gap below, which this generator does not yet address (no per-version interface codegen).

### [`ffi`](ffi/README.md) — raw C-ABI bindings

Generated, zero-dependency C-ABI surface (`GdxFfi`, `GdExtTypes`, `GdExtInterface`, `CStruct*`). Depended on by `core`; nothing else should need it directly. Mirrors godot-rust's `godot-ffi` crate being a sibling of `godot-core` rather than nested inside it.

### [`core`](core/README.md) — runtime

Hand-written Scala on top of [`ffi`](ffi/README.md)'s C-ABI bindings: class registration, `Gd[T]`, variant marshalling, `GdArray`/`GdDict`, signals, Zones. Everything else in the binding is built on this. A handful of files that need `gdext.core` types but are otherwise mechanically generated (heap builtin handles, packed array extensions, the string-name cache) live in `core/src/gdext/core/generated/` — same package, separated from hand-written code so it's clear at a glance which files are generator output.
**Known issues** (from [`FEATURES.md`](../FEATURES.md#known-limitations)):
- `GdArray`/`GdDict` don't manage the internal Godot Array/Dictionary refcount — call `.destroy()` on locals, or treat exported collections as extension-lifetime handles.
- `FromVariant` for `GdArray`/`GdDict` heap-allocates 8 bytes per read (bounded leak).
- `GdxApiV47` (`core/src/gdext/core/v47/GdxApiV47.scala`) is a stub — version-specific API loading for post-4.7 features isn't implemented; icon registration works via a hardcoded pointer in `GdxApi.initialize` instead.
- Signal arity is capped at `Signal0`–`Signal8`.

### [`generator`](generator/README.md) — api generator

Standalone Mill tool, depends on `gdext.core` types it emits references to. Reads both API JSONs and produces idiomatic Scala: engine class wrappers, virtuals, builtins, utility functions, global scope.
**Known issues:** ScalaDoc from `extension_api.json` is not yet forwarded to generated wrappers; class registration order in `GeneratedRegistrations.scala` depends on scan order, so a user `Child extends Parent` requires `Parent` to be scanned first.

### [`generated`](generated/README.md) — api layer

Compiled output of the `generator`. Idiomatic, game-dev-facing bindings: `classes/`, `virtuals/`, `types/`, builtins, utility functions, global scope. Depends on `core`.
**Known issues:** named-constant/convenience methods on some builtin value types are still being filled in (see roadmap in `FEATURES.md`); `@async`/`@await`, `@rpc`, and `@globalClass` annotations are defined but not yet wired through codegen.

### `gdext` (top-level module)

Aggregates `ffi` + `core` + `generated` into the single dependency examples and user projects consume (`mvnDeps = Seq(mvn"com.julian-avar::gdext::0.1.0")`). Source lives in `gdext/src/gdext/` — the `api` object is the re-export surface (`gdext.api.*`), alongside `GodotEntry`, `lowlevel`, `editor/` (editor plugin base), and `scala/` (the `ScalaScript` language integration so `.scala` files can be used as Godot scripts).

## See also

- [`docs/00-architecture-overview.md`](../docs/00-architecture-overview.md) — module-to-Rust-analogue map and data-flow diagrams (build-time codegen, runtime call flow, virtual dispatch)
- [`FEATURES.md`](../FEATURES.md) — full feature list, known limitations, and roadmap (canonical; the per-module lists above are a categorized excerpt and may drift — check there for the current full picture)
- [`docs/`](../docs/) — architecture notes on ownership, lifecycle, Zones, and code generation referenced throughout the module READMEs
