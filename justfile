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

# Build the Scaladoc site for `module` (api, core, or ffi) and open it in a browser
# `godot` controls which Godot API version's generated sources get documented (4.5.0, 4.6.1, 4.7.0, ...)
docs module="api" godot="4.7.0":
    ./mill gdext.{{ module }}.{{ replace(godot, ".", "_") }}.docJar
    rm -rf /tmp/gdext-{{ module }}-docs
    unzip -q -o out/gdext/{{ module }}/{{ godot }}/docJar.dest/out.jar -d /tmp/gdext-{{ module }}-docs
    xdg-open /tmp/gdext-{{ module }}-docs/index.html

lint:
    ./mill mill.scalalib.scalafmt

new-setup:
    ./mill gdext.ffi.4_7_0.compile
