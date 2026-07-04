package net.`julian-avar`.gdext

import net.`julian-avar`.gdext

/** Low-level API for library and plugin authors.
  *
  * Game code should use `gdext.api.*` instead.
  *
  * Exposes raw GDExtension FFI access:
  *   - [[GdxFfi]] — raw function-pointer table (one `Ptr[Byte]` var per interface fn)
  *   - [[GdxApi]] — typed wrappers around the GDExtension FFI
  *   - Core bootstrap types for manual class registration
  *
  * C-ABI type aliases (`GDExtensionInt`, `GDExtensionVariantPtr`, etc.) live in `gdext.ffi` —
  * import that package directly for sys-layer types.
  */
object lowlevel:
    // Packages can't be used as export prefixes, so we alias the private[gdext] objects
    // directly. The aliases are public; the originals remain package-private.
    val GdxFfi = gdext.ffi.GdxFfi
    val GdxApi = gdext.core.GdxApi

    type GetProcAddressFn   = gdext.core.GetProcAddressFn
    type GdxInitStruct      = gdext.core.GdxInitStruct
    type ClassCreationInfo2 = gdext.core.ClassCreationInfo2

    val GdxInitLevel = gdext.core.GdxInitLevel
end lowlevel
