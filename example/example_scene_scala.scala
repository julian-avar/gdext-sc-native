package example

import gdext.core.*
import gdext.generated.*

@gdclass
class ExampleSceneScala extends CenterContainer:
    var toggled = false

    override def _ready(): Unit =
        GdxApi.printString("Hello, Scala!")

        val btn = findChild("Button").as(new Button(_))

        btn.connect("pressed"): () =>
            toggled = !toggled
            val tint = if toggled then 0.3f else 1f
            btn.modulate = Color(1f, tint, tint, 1f)
    end _ready
end ExampleSceneScala
