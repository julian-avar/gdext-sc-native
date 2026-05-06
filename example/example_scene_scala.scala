package example

import gdext.*
import gdext.generated.*

@gdclass
class ExampleSceneScala extends CenterContainer:
    override def _ready(): Unit =
        println("ExampleSceneScala._ready() called")
        GdxApi.printString("Hello, Scala!")
end ExampleSceneScala
