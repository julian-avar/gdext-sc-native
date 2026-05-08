package example.rigid_body_example

import gdext.core.*
import gdext.generated.*

@gdclass
class PlayerSc extends CharacterBody2D:
    @`export`
    var speed = 400

    override def _physicsProcess(_delta: Double): Unit =
        getInput()
        moveAndSlide()

    def getInput(): Unit =
        var input_direction: Vector2 =
            // Input.get_vector("player_b_left", "player_b_right", "player_b_up", "player_b_down")
            ???
        this.velocity =
            // input_direction * speed
            // Vector2(input_direction.x * speed, input_direction.y * speed)
            ???
    end getInput
end PlayerSc
