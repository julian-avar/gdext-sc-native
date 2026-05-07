package example

import gdext.*
import gdext.generated.*
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.string.memset
import scala.scalanative.libc.stdlib.malloc

// Static callback — CFuncPtr lambdas cannot close over instance fields.
// Per-instance state is passed via userdata (a 16-byte malloc'd buffer):
//   offset 0 (Byte):     toggled flag (0 = false, 1 = true)
//   offset 8 (Ptr[Byte]): button node pointer
object ExampleSceneScala:
    val onPressedCb: CallableCallFn = CFuncPtr5
        .fromScalaFunction[Ptr[Byte], Ptr[Ptr[Byte]], Long, Ptr[Byte], Ptr[Byte], Unit] {
            (userdata, _, _, _, _) =>
                val wasToggled = !userdata != 0.toByte
                val nowToggled = !wasToggled
                !userdata = if nowToggled then 1.toByte else 0.toByte
                val buttonPtr = !(userdata + 8).asInstanceOf[Ptr[Ptr[Byte]]]
                val tint = if nowToggled then 0.3f else 1f
                new CanvasItem(buttonPtr).setModulate(Color(1f, tint, tint, 1f))
        }
end ExampleSceneScala

@gdclass
class ExampleSceneScala extends CenterContainer:
    // 16-byte heap buffer: [byte toggled | 7-byte pad | Ptr[Byte] buttonPtr]
    // Kept alive for the lifetime of this instance (Godot calls the callable).
    private val statePtr: Ptr[Byte] = malloc(16).asInstanceOf[Ptr[Byte]]

    override def _ready(): Unit =
        GdxApi.printString("Hello, Scala!")
        memset(statePtr, 0, 16.toUSize)
        val btn = findChild(c"Button")
        !(statePtr + 8).asInstanceOf[Ptr[Ptr[Byte]]] = btn.ptr
        GdxApi.connectSignal(btn.ptr, c"pressed", ExampleSceneScala.onPressedCb, statePtr)
    end _ready
end ExampleSceneScala
