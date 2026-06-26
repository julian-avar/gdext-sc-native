package gdext

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

    // ── Entry point helpers ───────────────────────────────────────────────────
    // Re-exports the top-level GodotEntry (which includes ScalaScript registration).
    // Use `gdext.GodotEntry.init(...)` by FQN in entry files to avoid the local
    // `object GodotEntry` shadowing this re-export.
    export gdext.GodotEntry

    type GetProcAddressFn = gdext.core.GetProcAddressFn
    type GdxInitStruct    = gdext.core.GdxInitStruct
end api
