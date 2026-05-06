package gdext

import scala.scalanative.unsafe.*

/** Trait for classes registered with @gdclass. Override the virtual methods you need.
  */
trait GdRegistered:
    protected[gdext] var _godotPtr: Ptr[Byte] = null

    def setGodotPtr(ptr: Ptr[Byte]): Unit = _godotPtr = ptr

    def onReady(): Unit                       = ()
    def onProcess(delta: Double): Unit        = ()
    def onPhysicsProcess(delta: Double): Unit = ()
end GdRegistered
