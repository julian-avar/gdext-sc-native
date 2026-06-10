# Prepend with `nix develop -c` if not already in a nix shell
default:
    just --list

# `example` being any module under `examples/`
run example:
    mill gdext.generator.generate

    mill examples.{{ example }}.buildExtension
    godot4.5 examples/{{ example }}/project.godot

lint:
    mill mill.scalalib.scalafmt
