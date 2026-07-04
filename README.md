# GDExtension for Scala

State: Amoeba

Complete API breakeage should be expected

AI allowed.

## Using the project

On the root folder of your Godot project, add a `build.mill.yaml` file that should look like this:

```yaml
mill-version: 1.1.2
mill-build:
  mvnDeps:
  - net.julian-avar::gdext-mill-plugin::0.1.0
extends: net.julian-avar.gdext.godotscalanativelib.GodotScalaNativeModule
godotVersion: 4.7.0
scalaVersion: 3.8.4
scalaNativeVersion: 0.5.11
mvnDeps:
- net.julian-avar::gdext::0.1.0
```

## Building the project

Edit flake.nix to match the godot version you're trying to use.

Make sure you have Nix installed, then run `nix develop` to enter the development environment. `.envrc` is included in case `direnv` is available. That should take care of the environment which includes:

- Mill
  - Scala 3.8.4
- JDK 21
- GraalVM dependencies
- LLVM
- Godot 4.5/4.6/4.7

The project's infra uses GraalVM, but the actual user code runs on Scala Native (which is pulled using Coursier). Garbage collection strategies have not been fully tested yet.

`gdext` is built as a real Mill plugin: `gdext.ffi`/`gdext.core`/`gdext.api` are cross-built once per
supported Godot version (4.5.0, 4.6.1, 4.7.0), and codegen runs automatically as part of compiling
those modules — there's no separate "generate" step to invoke. Each `examples/*` directory is its own
standalone Mill build that consumes `gdext`/`gdext-mill-plugin` from `~/.ivy2/local`, so publish
locally before building an example.

Once you're in the nix shell:

```sh
just publishLocal        # publish gdext + gdext-mill-plugin to ~/.ivy2/local (godot=4.7.0 by default)
just run rigid_body      # publishLocal, then build + launch examples/rigid_body against Godot 4.7.0
just run rigid_body 4.5.0

just lint
# ./mill mill.scalalib.scalafmt
```
