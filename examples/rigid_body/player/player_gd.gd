extends CharacterBody2D

@export var speed = 400

func _physics_process(_delta: float) -> void:
	get_input()
	move_and_slide()

func get_input() -> void:
	var input_direction = Input.get_vector("player_a_left", "player_a_right", "player_a_up", "player_a_down")
	velocity = input_direction * speed
