extends CenterContainer

var toggled := false

func _ready() -> void:
	print("Hello, GDScript!")

	var btn := get_node("Button")

	btn.connect("pressed", func():
		toggled = !toggled
		var tint = 0.3 if toggled else 1
		btn.modulate = Color(1, tint, tint, 1)
	)
