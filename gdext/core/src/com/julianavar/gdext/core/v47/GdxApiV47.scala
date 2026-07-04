package com.julianavar.gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

// Godot 4.8+ interface functions.
// Called from GdxApi.initialize after the base interface is set up.
// GdxApi null-guards all uses, so any unloaded pointer degrades gracefully.
private[gdext] object GdxApiV47:
    private type GetGodotVersionFn = CFuncPtr1[Ptr[Byte], Unit]

    def init(getProcAddr: GetProcAddressFn): Unit =
        val getVersionAddr = getProcAddr(c"get_godot_version")
        if getVersionAddr == null then return
        val getVersion = CFuncPtr.fromPtr[GetGodotVersionFn](getVersionAddr)
        val versionBuf = stackalloc[Byte](24) // major, minor, patch: UInt32; string: pointer
        getVersion(versionBuf)
        val major = !versionBuf.asInstanceOf[Ptr[UInt]]
        val minor = !(versionBuf + 4).asInstanceOf[Ptr[UInt]]

        // classdb_register_extension_class_icon was added after 4.7.stable.
        if major > 4.toUInt || (major == 4.toUInt && minor >= 8.toUInt) then
            GdxApi.registerIconFnAddr = getProcAddr(c"classdb_register_extension_class_icon")
    end init
end GdxApiV47
