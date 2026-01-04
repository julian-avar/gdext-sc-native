# godot.sc - Scala Native Bridge for Godot

## Project Overview

**godot.sc** is a Scala Native binding for the Godot game engine, enabling developers to write Godot games and extensions in Scala. The architecture is inspired by [SwiftGodot](https://github.com/migueldeicaza/SwiftGodot), leveraging Godot's **GDExtension** system to provide seamless interoperability between Scala Native and the Godot engine.

## Why Scala Native?

Scala Native compiles Scala code to native executables via LLVM, making it an excellent fit for game development:

- **No Garbage Collection Pauses**: Like Swift's ARC, Scala Native avoids GC stutters that plague traditional JVM-based game code
- **Native Performance**: LLVM-compiled code runs at near-C speeds
- **Type Safety**: Scala's strong type system catches errors at compile time
- **Functional Programming**: Pattern matching, immutability, and higher-order functions improve code quality
- **Interop**: Scala Native's C interop makes it natural to bind with Godot's C-based GDExtension API

## Architecture (Based on SwiftGodot Model)

### Core Bridge Technology: GDExtension

GDExtension is Godot's modern extension system that allows language bindings without modifying the engine. It exposes the complete Godot API through a C interface defined in `gdextension_interface.h`.

### Two-Tier Library Design

Following SwiftGodot's approach, godot.sc provides two compilation modes:

1. **godot-runtime**: Minimal core with essential types (Object, RefCounted, Variant, ClassDB)
   - Smaller binary size
   - Foundation for custom bindings

2. **godot**: Full API including all generated bindings
   - Complete Godot class hierarchy (Node, Node2D, Node3D, Resource, etc.)
   - All engine methods and properties
   - Signals and virtual method support

### Code Generation

The binding generation process:

1. **API Extraction**: Parse `extension_api.json` from Godot source
2. **Scala Generation**: Generate Scala Native code with:
   - Class hierarchies matching Godot's inheritance
   - Method bindings using `@extern` and function pointers
   - Property accessors
   - Signal definitions
   - Virtual method hooks

3. **Macro-based Registration**: Use Scala 3 macros for automatic class registration
   ```scala
   @GodotClass
   class Player extends CharacterBody2D {
     @GodotExport var speed: Float = 400.0f

     @GodotMethod
     override def _process(delta: Double): Unit = {
       // Game logic here
     }
   }
   ```

### Extension Registration

The entry point mechanism:
- A Mill plugin scans compiled classes for `@GodotClass` annotations
- Generates C-compatible entry point functions
- Registers all classes with Godot's ClassDB on initialization
- Creates `.gdextension` configuration file

## Build System: Mill

Using [Mill](https://mill-build.com/) instead of sbt for several advantages:

- **Fast**: Mill's incremental compilation is significantly faster than sbt
- **Simple**: Direct task definition without complex DSL
- **Scala Native Integration**: First-class support for Scala Native builds
- **Plugin System**: Easy to create custom plugins for code generation

### Expected Build Structure

```
build.sc                          # Mill build definition
godot.sc/
├── runtime/                      # Core runtime library
│   ├── src/
│   │   └── godot/
│   │       ├── core/            # Variant, String, Vector2, etc.
│   │       ├── internal/        # GDExtension FFI bindings
│   │       └── macros/          # @GodotClass, @GodotExport, etc.
│   └── test/
├── generator/                    # Code generator (JVM-based)
│   └── src/
│       └── godot/generator/
│           ├── Parser.scala     # Parse extension_api.json
│           ├── Generator.scala  # Generate Scala Native code
│           └── Templates.scala  # Code templates
├── bindings/                     # Generated full API
│   └── src/                     # (Generated, not committed)
│       └── godot/
│           ├── Node.scala
│           ├── Node2D.scala
│           └── ...
└── examples/                     # Example games/extensions
    └── my-game/
        ├── src/
        └── project.godot
```

## Development Phases

### Phase 1: Foundation
- [ ] Set up Mill build with Scala Native support
- [ ] Implement GDExtension C FFI bindings
- [ ] Create core types: Variant, String, StringName
- [ ] Basic Object and RefCounted base classes
- [ ] Simple extension initialization

### Phase 2: Core Runtime
- [ ] Math types: Vector2, Vector3, Rect2, Transform2D, etc.
- [ ] Variant type system with proper conversions
- [ ] ClassDB registration mechanism
- [ ] Property and method binding infrastructure
- [ ] Signal support

### Phase 3: Code Generation
- [ ] Parse `extension_api.json`
- [ ] Generate class hierarchies
- [ ] Generate method bindings
- [ ] Generate property accessors
- [ ] Virtual method support
- [ ] Mill plugin for automatic generation

### Phase 4: Macro System
- [ ] `@GodotClass` annotation
- [ ] `@GodotExport` for properties
- [ ] `@GodotMethod` for callable methods
- [ ] `@GodotSignal` for custom signals
- [ ] Automatic registration generation

### Phase 5: Complete Bindings
- [ ] Generate all engine classes
- [ ] Node hierarchy (Node, Node2D, Node3D, Control, etc.)
- [ ] Resource system
- [ ] Input handling
- [ ] Scene management

### Phase 6: Tooling & Polish
- [ ] gdextension file generation
- [ ] Example projects
- [ ] Documentation
- [ ] Testing infrastructure
- [ ] CI/CD setup

## Technical Challenges

### Memory Management
- **Challenge**: Bridging Scala Native's GC with Godot's reference counting
- **Solution**: Use RefCounted for Godot objects, careful ownership tracking in Object wrappers

### C Interop
- **Challenge**: Godot uses C-style callbacks and function pointers extensively
- **Solution**: Scala Native's `@extern` objects and CFuncPtr types

### Variant System
- **Challenge**: Godot's Variant is a dynamically-typed union supporting 20+ types
- **Solution**: Scala sealed trait with implicit conversions, similar to:
  ```scala
  sealed trait Variant
  object Variant {
    given Conversion[Int, Variant] = IntVariant(_)
    given Conversion[String, Variant] = StringVariant(_)
    // etc.
  }
  ```

### Virtual Methods
- **Challenge**: Godot calls overridden virtual methods like `_ready()`, `_process()`
- **Solution**: Register callbacks in ClassDB that dispatch to Scala instances

### String Interop
- **Challenge**: Godot uses UTF-32 strings, Scala uses UTF-16
- **Solution**: String/StringName wrapper types with proper encoding conversion

### Build Integration
- **Challenge**: Integrating with Godot's project structure
- **Solution**: Generate `.gdextension` files, compile to shared libraries that Godot loads

## Comparison with Other Godot Bindings

| Feature | godot.sc (Scala Native) | SwiftGodot | godot-rust | C# |
|---------|------------------------|------------|------------|-----|
| GC Pauses | None (Immix GC) | None (ARC) | None (Ownership) | Yes |
| Native Performance | Yes | Yes | Yes | JIT/AOT |
| Type Safety | Strong (Scala) | Strong (Swift) | Strong (Rust) | Strong (C#) |
| Functional Programming | Excellent | Good | Good | Limited |
| Interop Complexity | Medium | Low | Medium | Low |
| Ecosystem | Growing | Apple-focused | Rust crates | .NET libraries |

## Project Goals

1. **Zero-Cost Abstractions**: Bindings should compile to optimal native code
2. **Idiomatic Scala**: API should feel natural to Scala developers
3. **Complete Coverage**: Support the entire Godot API
4. **Maintainability**: Automated generation from `extension_api.json`
5. **Performance**: Match or exceed C++ performance
6. **Type Safety**: Leverage Scala's type system to catch errors early

## Getting Started (Future)

Once the project is ready, usage will look like:

```scala
// build.sc
import mill._, scalalib._, scalanativelib._

object game extends ScalaNativeModule {
  def scalaVersion = "3.4.0"
  def scalaNativeVersion = "0.5.0"

  def ivyDeps = Agg(
    ivy"com.github.godot-sc::godot::0.1.0"
  )
}
```

```scala
// src/Player.scala
import godot._
import godot.annotation._

@GodotClass
class Player extends CharacterBody2D {
  @GodotExport var speed = 400.0
  @GodotExport var jumpVelocity = -800.0

  @GodotMethod
  override def _physicsProcess(delta: Double): Unit = {
    val velocity = getVelocity()

    if Input.isActionPressed("jump") && isOnFloor() then
      velocity.y = jumpVelocity

    val direction = Input.getAxis("ui_left", "ui_right")
    velocity.x = direction * speed

    setVelocity(velocity)
    moveAndSlide()
  }
}
```

## Resources

- [Godot GDExtension Documentation](https://docs.godotengine.org/en/stable/tutorials/scripting/gdextension/index.html)
- [SwiftGodot](https://github.com/migueldeicaza/SwiftGodot) - Primary architectural inspiration
- [godot-rust](https://github.com/godot-rust/gdext) - Alternative reference implementation
- [Scala Native](https://scala-native.org/) - Compilation target
- [Mill Build Tool](https://mill-build.com/) - Build system

## License

TBD

---

**Status**: Early development / Planning phase
**Maintainer**: TBD
**Contributions**: TBD
