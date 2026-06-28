# GDExtension for Scala

State: Amoeba

Complete API breakeage should be expected

AI allowed.

## Using the project

TBD

## Building the project

Make sure you have Nix installed, then run `nix develop` to enter the development environment. `.envrc` is included in case `direnv` is available. That should take care of the environment which includes:

- Mill
  - Scala 3.8.3
- JDK 21
- GraalVM dependencies
- LLVM
- Godot 4.5/4.6

The project's infra uses GraalVM, but the actual user code runs on Scala Native (which is pulled using Coursier). Garbage collection strategies have not been fully tested yet.

Once you're in the nix shell:

```sh
just run rigid_body
# mill gdext.generator.generate
# mill examples.rigid_body.buildExtension
# godot4.5 examples/rigid_body/project.godot

just lint
# mill mill.scalalib.scalafmt
```
