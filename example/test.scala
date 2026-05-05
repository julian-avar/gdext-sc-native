package mygame

import gdext.*
import gdext.godot.*

@gdclass
class SpinningCube extends Node3D:
    override def _ready(): Unit =
        val meshRender = MeshInstance3D()
        meshRender.mesh = BoxMesh()
        addChild(meshRender)

    override def _process(delta: Double): Unit =
        rotateY(delta.toFloat)
end SpinningCube
