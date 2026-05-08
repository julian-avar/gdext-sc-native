





class_name ExampleSceneGDScript extends CenterContainer
var toggled := false

func _ready() -> void:
	print("Hello, GDScript!")
	
	var btn := get_node("Button")
	
	btn.connect("pressed", func():
		toggled = !toggled
		val tint = 0.3 if toggled else 1
		btn.set_modulate(Color(1, tint, tint, 1))
	)
