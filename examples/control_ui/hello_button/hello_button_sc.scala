package example.control_ui_example

import com.julianavar.gdext.api.*
import com.julianavar.gdext.generated.*

@gdclass class HelloButtonSc extends CenterContainer:
    var toggled = false

    @onready lazy val btn = $"Button".as[Button]

    override def ready(): Unit = print("Hello, Scala!")

    @func def _onButtonPressed(): Unit =
        toggled = !toggled
        val tint = if toggled then 0.3f else 1f
        btn.modulate = Color(1f, tint, tint, 1f)
    end _onButtonPressed
end HelloButtonSc
