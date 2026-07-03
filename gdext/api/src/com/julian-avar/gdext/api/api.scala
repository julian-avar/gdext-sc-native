package com.`julian-avar`.gdext

import com.`julian-avar`.gdext

/** Public API surface for game code.
  *
  * Game classes import `gdext.api.*` for annotations and registration, alongside the specific
  * packages they need:
  *   - `gdext.generated.*` — generated engine class wrappers and their virtual stubs
  *   - `gdext.core.*` — if direct FFI access is required (advanced)
  *
  * Typical game-class imports:
  * {{{
  * import gdext.api.*
  * import gdext.generated.*
  * }}}
  */
object api:
    // ── Annotations ──────────────────────────────────────────────────────────
    export gdext.core.gdclass
    export gdext.core.gdexport
    export gdext.core.func
    export gdext.core.gdenum
    export gdext.core.signal
    export gdext.core.tool
    export gdext.core.onready
    export gdext.core.export_category
    export gdext.core.export_group
    export gdext.core.export_subgroup

    // ── Registration ─────────────────────────────────────────────────────────
    export gdext.core.Register
    export gdext.core.GdClassRegistry

    // ── Base types ───────────────────────────────────────────────────────────
    export gdext.core.GodotObject
    export gdext.core.Gd
    export gdext.core.GodotClass

    // ── Variant typeclasses (for custom ToVariant/FromVariant instances) ──────
    export gdext.core.ToVariant
    export gdext.core.FromVariant

    // ── Signal handles ────────────────────────────────────────────────────────
    export gdext.core.{Signal0, Signal1, Signal2, Signal3, ConnectionToken}

    // ── Callable lambda factories ─────────────────────────────────────────────
    export gdext.core.CallableLambda

    // ── Godot output + typed null ─────────────────────────────────────────────
    // print / printerr come from gdext.generated.* (top-level GlobalScope functions).
    // Export only what doesn't conflict with generated.
    export gdext.godot.{println, nullOf, Log}

    // Note: `Zone` and `alloc` come from `scala.scalanative.unsafe.*`.
    // Add that import alongside `gdext.api.*` when working with value-type engine methods.

    // ── Editor integration ────────────────────────────────────────────────────
    export gdext.editor.ScalaEditorPlugin

    // ── Entry point helpers ───────────────────────────────────────────────────
    export gdext.GodotEntry

    // ── Low-level API (library/plugin authors) ────────────────────────────────
    export gdext.lowlevel

    type GetProcAddressFn = gdext.core.GetProcAddressFn
    type GdxInitStruct    = gdext.core.GdxInitStruct
end api
