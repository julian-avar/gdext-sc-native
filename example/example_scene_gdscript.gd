extends CenterContainer


var toggled := false


func _ready() -> void:
	print("Hello, GDScript!")


func _on_button_pressed() -> void:
	toggled = !toggled
	
	var btn := get_node("Button")
	var tint = 0.3 if toggled else 1.0
	btn.modulate = Color(1, tint, tint, 1)
