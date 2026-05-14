package example.control_ui_example

import gdext.core.*
import gdext.generated.*

@gdclass
class ExampleSceneScala extends CenterContainer:
    var toggled = false

    // @onready var btn = $"Button"
    // @onready var btn = findChild("Button").as[Button]
    // @onready var btn = findChild("Button").as(new Button(_))

    override def _ready(): Unit = GdxApi.printString("Hello, Scala!")
    // val btn = findChild("Button").as(new Button(_))
    // btn.connect("pressed")(() => _onButtonPressed())
    end _ready

    def _onButtonPressed(): Unit =
        toggled = !toggled

        // val btn  = $"Button"
        // val btn  = findChild("Button").as[Button]
        val btn  = findChild("Button").as(new Button(_))
        val tint = if toggled then 0.3f else 1f
        btn.modulate = Color(1f, tint, tint, 1f)
    end _onButtonPressed
end ExampleSceneScala
