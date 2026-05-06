package example

import gdext.*
import gdext.generated.*

@gdclass
class ExampleSceneScala extends CenterContainer:
    override def _ready(): Unit                = GdxApi.printString("Hello, Scala!")
    override def _process(delta: Double): Unit = GdxApi.printString(s"delta: $delta")
end ExampleSceneScala
