package example

import gdext.core.*
import scala.scalanative.unsafe.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*
import gdext.generated.CenterContainerVirtuals
import gdext.generated.CharacterBody2DVirtuals
import gdext.generated.InputMap
import gdext.generated.Engine
import example.rigid_body_example.PlayerSc
import example.control_ui_example.ExampleSceneScala
import example.scripting.{ScalaScript, ScalaScriptLanguage, ScalaScriptLanguageSupport}
import gdext.core.VariantType
import gdext.core.Variant
import gdext.core.PropertyDescriptor
import gdext.core.method.MethodEntry

/** GDExtension entry point — owned by the user project, not the library.
  *
  * Binds are loaded lazily on first method call — no explicit loadBinds() needed.
  */
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
        GdClassRegistry.register(
          "ExampleSceneScala",
          "CenterContainer",
          () => new ExampleSceneScala(),
          CenterContainerVirtuals.entries,
          methods = List(MethodEntry(
            "_on_button_pressed",
            (obj, _, _) => obj.asInstanceOf[ExampleSceneScala]._onButtonPressed()
          ))
        )
        GdClassRegistry.register(
          "PlayerSc",
          "CharacterBody2D",
          () => new PlayerSc(),
          CharacterBody2DVirtuals.entries,
          properties = List(PropertyDescriptor(
            name = "speed",
            variantType = VariantType.Int,
            getter = (obj, ret) => Variant.writeInt(ret, obj.asInstanceOf[PlayerSc].speed.toLong),
            setter = (obj, v) => obj.asInstanceOf[PlayerSc].speed = Variant.readInt(v).toInt
          ))
        )
        gdext.core.GodotEntry.init(
          getProcAddress,
          library,
          initPtr,
          () =>
              val inputMap = new InputMap(GdxApi.getSingleton(c"InputMap"))
              inputMap.loadFromProjectSettings()

              val snBuf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
              memset(snBuf, 0, StringNameSize)
              GdxApi.initStringName(snBuf, c"ScalaScriptLanguage")
              val langPtr = GdxApi.constructObject(snBuf)
              ScalaScriptLanguageSupport.langInstance = new ScalaScriptLanguage(langPtr)
              Engine.registerScriptLanguage(ScalaScriptLanguageSupport.langInstance)
        )
    end godotScalaInit
end GodotEntry
