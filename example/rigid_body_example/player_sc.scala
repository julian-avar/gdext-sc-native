package example.rigid_body_example

import gdext.core.*
import gdext.godot.*
import gdext.godot.Predef.*
import gdext.generated.*

@gdclass
class PlayerSc extends CharacterBody2D:
    @`export`
    var speed = 400

    override def _physicsProcess(_delta: Double): Unit =
        getInput()
        moveAndSlide()

    private def getInput(): Unit =
        val dir = Input.getVector("player_b_left", "player_b_right", "player_b_up", "player_b_down")
        this.velocity = dir * speed.toFloat
    end getInput
end PlayerSc
