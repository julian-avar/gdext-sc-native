package example.scala_script

import com.`julian-avar`.gdext.api.*
import com.`julian-avar`.gdext.generated.*

/** Minimal Scala example node.
  *
  * To wire up from the Godot editor:
  *   1. Build the extension (`just run scala_script`).
  *   2. Open this scene. The root node type is `ScalaScriptExampleSc`.
  *   3. Select the Button child, go to Node → Signals → pressed, and connect
  *      it to the root node. Godot will offer `_on_button_pressed` as the
  *      target method (registered via `@func` below).
  *   4. Godot writes the connection into the `.tscn` automatically.
  */
@gdclass class ScalaScriptExampleSc extends Node2D:

    @func def _onButtonPressed(): Unit =
        print("Button pressed!")

end ScalaScriptExampleSc
