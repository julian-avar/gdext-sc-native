package examples.rigid_body

import gdext.core.*
import gdext.generated.*

@gdclass class PlayerSc extends CharacterBody2D:
    @gdexport var speed = 400

    override def _physicsProcess(delta: Double): Unit =
        getInput()
        moveAndSlide()

    private def getInput(): Unit =
        val dir = Input.getVector("player_b_left", "player_b_right", "player_b_up", "player_b_down")
        this.velocity = dir * speed.toFloat
    end getInput
end PlayerSc
