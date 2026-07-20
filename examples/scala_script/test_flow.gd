extends SceneTree
func _initialize() -> void:
	var scr: Script = ClassDB.instantiate("ScalaScript")
	scr.set_source_code("@gdclass class TestGen extends Node2D\n")
	var err := ResourceSaver.save(scr, "res://__gen_test.scala")
	print("save err: ", err)
	if err == OK:
		var loaded: Script = ResourceLoader.load("res://__gen_test.scala")
		print("loaded ok: ", loaded != null and loaded.get_source_code() == scr.get_source_code())
	quit()
