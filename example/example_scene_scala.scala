package example

import gdext.*
import gdext.generated.*

@gdclass
class ExampleSceneScala extends CenterContainer:
    override def _ready(): Unit = print("Hello, GDScript!")
