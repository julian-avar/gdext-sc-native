default:
    just --list

run:
    # nix develop
    nix develop -c mill gdext.generator-module.generate
    nix develop -c mill example.buildExtension
    nix develop -c godot4.5 example/project.godot

lint:
    nix develop -c mill mill.scalalib.scalafmt
