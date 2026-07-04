package com.julianavar.gdext
package godotscalanativelib.ffi.generators

/** Shared C-to-Scala type mapping used by all core generators. */
object TypeMapper:
    val typeMap: Map[String, String] = Map(
      "void"     -> "CVoidPtr",
      "int8_t"   -> "CSignedChar",
      "uint8_t"  -> "UByte",
      "int16_t"  -> "Short",
      "uint16_t" -> "UShort",
      "int32_t"  -> "CInt",
      "uint32_t" -> "CUnsignedInt",
      "int64_t"  -> "CLongLong",
      "uint64_t" -> "CUnsignedLongLong",
      "size_t"   -> "CSize",
      "char"     -> "CChar",
      "char16_t" -> "CChar16",
      "char32_t" -> "CChar32",
      "wchar_t"  -> "CWideChar",
      "float"    -> "CFloat",
      "double"   -> "CDouble"
    )

    def scalaTypeName(cType: String): String =
        if cType.endsWith("*") then
            val rawType = cType.stripPrefix("const ").stripSuffix("*").trim
            if rawType == "void" then "CVoidPtr" else s"Ptr[${typeMap.getOrElse(rawType, rawType)}]"
        else typeMap.getOrElse(cType, cType)
    end scalaTypeName
end TypeMapper
