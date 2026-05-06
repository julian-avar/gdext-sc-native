package example

import gdext.*
import gdext.generated.*

@gdclass
class SpinningCube extends Node3D:
    override def _ready(): Unit =
        val meshRender = MeshInstance3D()
        meshRender.mesh = BoxMesh()
        addChild(meshRender)
    end _ready

    override def _process(delta: Double): Unit = rotateY(delta.toFloat)
end SpinningCube
