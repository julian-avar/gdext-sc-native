default:
    just --list


run:
    # nix develop
    nix develop -c mill gdext.generator-module.generate
    nix develop -c mill gdext.buildExtension
    nix develop -c godot4.5 example/project.godot
