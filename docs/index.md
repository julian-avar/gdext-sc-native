# gdext-scala-native

A Scala Native binding for Godot's GDExtension API, generated directly from Godot's own
`extension_api.json` and `doc/classes` documentation.

This site has two parts:

- The **API reference** (the package tree in the sidebar/search) — every generated engine class,
  builtin type, and utility function, with descriptions carried over from Godot's own docs.
- The **guides** below — how the binding itself is put together internally: ownership, memory,
  the generator, and the runtime.

## Guides

- [Architecture Overview](00-architecture-overview.md)
- [Two Parallel Ownership Systems](01-two-ownership-systems.md)
- [`Gd[T]` Lifecycle](02-gd-lifecycle.md)
- [Variant Lifecycle](03-variant-lifecycle.md)
- [GdArray / GdDict Refcounting](04-gdarray-gddict-refcounting.md)
- [Zone System](05-zone-system.md)
- [CallbackRegistry, Signal Connection, and ConnectionToken](06-callback-signal-connection.md)
- [Class Registration Lifecycle](07-class-registration-lifecycle.md)
- [Hot-Reload Lifecycle](08-hot-reload.md)
- [PackedArrays](09-packed-arrays.md)
- [Memory Management Overview](10-memory-overview.md)
- [Generated Engine Class Wrappers](11-generated-engine-class-wrappers.md)
- [Generated Value Types](12-generated-value-types.md)
- [Generated Virtual Dispatch Tables](13-generated-virtual-dispatch.md)
