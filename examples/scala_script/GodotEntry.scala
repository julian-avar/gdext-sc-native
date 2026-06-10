package example

import gdext.core.*
import scala.scalanative.unsafe.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*
import gdext.generated.Engine
import gdext.scala.{ScalaScript, ScalaScriptLanguage, ScalaScriptLanguageSupport}

object GodotEntry:
    @exported("godot_scala_init")
    def godotScalaInit(
        getProcAddress: GetProcAddressFn,
        library: Ptr[Byte],
        initPtr: Ptr[GdxInitStruct]
    ): CUnsignedChar =
        GdClassRegistry.register(
          "ScalaScriptLanguage",
          "ScriptLanguageExtension",
          () => new ScalaScriptLanguage(),
          ScalaScriptLanguage.entries
        )
        GdClassRegistry.register(
          "ScalaScript",
          "ScriptExtension",
          () => new ScalaScript(),
          ScalaScript.entries
        )
        gdext.core.GodotEntry.init(
          getProcAddress,
          library,
          initPtr,
          () =>
              val snBuf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
              memset(snBuf, 0, StringNameSize)
              GdxApi.initStringName(snBuf, c"ScalaScriptLanguage")
              val langPtr = GdxApi.constructObject(snBuf)
              ScalaScriptLanguageSupport.langInstance = new ScalaScriptLanguage(langPtr)
              Engine.registerScriptLanguage(ScalaScriptLanguageSupport.langInstance)
        )
    end godotScalaInit
end GodotEntry
