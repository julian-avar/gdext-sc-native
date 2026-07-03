package examples.rigid_body

import com.`julian-avar`.gdext.core.*
import com.`julian-avar`.gdext.generated.*
import scala.scalanative.unsafe.Zone

@gdclass class PlayerSc extends CharacterBody2D:
    @signal case class Moved(deltaX: Float, deltaY: Float)
    @signal case class Died()

    @gdexport var speed = 400

    override def _physicsProcess(delta: Double): Unit =
        getInput()
        moveAndSlide()
        this.moved.emitSignal(velocity.x, velocity.y)  // extension on PlayerSc, `Moved` is the case class

    private def getInput(): Unit =
        Zone {
            val dir = Input.getVector("player_b_left", "player_b_right", "player_b_up", "player_b_down")
            velocity = dir * speed.toFloat
        }
end PlayerSc
