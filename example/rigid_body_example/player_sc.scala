package example.rigid_body_example

import gdext.core.*
import gdext.godot.*
import gdext.generated.*
import scala.scalanative.unsafe.*

@gdclass
class PlayerSc extends CharacterBody2D:
    @`export`
    var speed = 400

    override def _ready(): Unit =
        val inputMap = new InputMap(GdxApi.getSingleton(c"InputMap"))
        val hasPb    = inputMap.hasAction("player_b_left")
        val hasPa    = inputMap.hasAction("player_a_left")
        val hasUi    = inputMap.hasAction("ui_right")
        GdxApi.printString(
          s"[PlayerSc._ready] InputMap: player_b_left=$hasPb player_a_left=$hasPa ui_right=$hasUi"
        )
        if !hasPb then
            inputMap.loadFromProjectSettings()
            val hasPbAfter = inputMap.hasAction("player_b_left")
            GdxApi.printString(s"[PlayerSc._ready] After loadFromProjectSettings: player_b_left=$hasPbAfter")

    override def _physicsProcess(_delta: Double): Unit =
        getInput()
        moveAndSlide()

    private def getInput(): Unit =
        val input = new Input(GdxApi.getSingleton(c"Input"))
        val dir   = input.getVector("player_b_left", "player_b_right", "player_b_up", "player_b_down")
        val vel   = stackalloc[Vector2]()
        vel.x = dir.x * speed.toFloat
        vel.y = dir.y * speed.toFloat
        this.velocity = vel
    end getInput
end PlayerSc
