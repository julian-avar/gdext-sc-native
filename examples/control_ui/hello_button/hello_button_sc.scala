package example.control_ui_example

import gdext.core.*
import gdext.generated.*

@gdclass
class ExampleSceneScala extends CenterContainer:
    var toggled = false

    @onready
    lazy val btn = $"Button".as(new Button(_))

    override def _ready(): Unit = GdxApi.printString("Hello, Scala!")

    def _onButtonPressed(): Unit =
        GdxApi.printString("_onButtonPressed called")
        toggled = !toggled
        val tint = if toggled then 0.3f else 1f
        GdxApi.printString("before modulate")
        btn.modulate = Color(1f, tint, tint, 1f)
        GdxApi.printString("after modulate")
    end _onButtonPressed
end ExampleSceneScala
