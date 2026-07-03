package com.`julian-avar`.gdext

import _root_.scala.scalanative.unsafe.*
import com.`julian-avar`.gdext
import gdext.core.*

/** Top-level binding entry point.
  *
  * Game projects call `gdext.GodotEntry.init` from their `@exported` function:
  * {{{
  * @exported("godot_scala_init")
  * def godotScalaInit(
  *     getProcAddress: GetProcAddressFn,
  *     library:        Ptr[Byte],
  *     initPtr:        Ptr[GdxInitStruct]
  * ): CUnsignedChar =
  *   GeneratedRegistrations.registerAll()
  *   gdext.GodotEntry.init(getProcAddress, library, initPtr)
  * }}}
  */
object GodotEntry:
    def init(
        getProcAddress: GetProcAddressFn,
        library: Ptr[Byte],
        initPtr: Ptr[GdxInitStruct],
        onSceneInit: () => Unit = null
    ): CUnsignedChar = gdext.core.GodotEntry.init(getProcAddress, library, initPtr, onSceneInit)
end GodotEntry
