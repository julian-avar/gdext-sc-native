# Prepend with `nix develop -c` if not already in a nix shell
default:
    just --list

# Publish gdext and gdext-mill-plugin to ~/.ivy2/local so example standalone builds can resolve them
# `godot` controls which Godot API version's artifacts get published (4.5.0, 4.6.1, 4.7.0, ...)
publishLocal godot="4.7.0":
    ./mill gdext.{{ replace(godot, ".", "_") }}.publishLocal + \
        gdext.api.{{ replace(godot, ".", "_") }}.publishLocal + \
        gdext.core.{{ replace(godot, ".", "_") }}.publishLocal + \
        gdext.ffi.{{ replace(godot, ".", "_") }}.publishLocal + \
        gdext.mill-plugin.publishLocal

# `example` being any directory under `examples/`
# `godot` controls which Godot API to target (4.5.0, 4.6.1, 4.7.0, ...)
run example godot="4.7.0": (publishLocal godot)
    cd examples/{{ example }} && ./mill buildExtension
    godot4 examples/{{ example }}/project.godot

lint:
    ./mill mill.scalalib.scalafmt

new-setup:
    ./mill gdext.ffi.4_7_0.compile
