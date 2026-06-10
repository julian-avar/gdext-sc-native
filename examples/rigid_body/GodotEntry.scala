package examples.rigid_body

import gdext.core.*
import scala.scalanative.unsafe.*
import gdext.generated.CharacterBody2DVirtuals
import gdext.generated.InputMap
import examples.rigid_body.PlayerSc
import gdext.core.VariantType
import gdext.core.Variant
import gdext.core.PropertyDescriptor

object GodotEntry:
    @exported("godot_scala_init")
    def godotScalaInit(
        getProcAddress: GetProcAddressFn,
        library: Ptr[Byte],
        initPtr: Ptr[GdxInitStruct]
    ): CUnsignedChar =
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
        )
    end godotScalaInit
end GodotEntry
