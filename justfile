default:
    just --list


run:
    # nix develop
    mill gdext.buildExtension
    godot4.5 example/project.godot
