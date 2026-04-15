# Scala Godot

Scala Native bindings for Godot Engine via GDExtension.

Write Godot games in Scala with native performance and no GC pauses!

## Project Structure

```
scala-godot/
├── runtime/          # Core handcoded types and GDExtension bindings
├── generator/        # Code generator (parses extension_api.json)
├── bindings/         # Generated Godot API bindings
├── examples/         # Example projects
│   └── simple2d/    # Simple 2D game example
├── build.mill          # Mill build definition
└── README.md         # This file
```

## Quick Start

### Prerequisites

- Mill 1.1.0-rc3 (included as `./mill`)
- Scala 3.7.4
- Scala Native 0.5.6
- LLVM toolchain
- Godot 4.x

### Building

```bash
# 1. Compile the generator
./mill generator.compile

# 2. Get extension_api.json from Godot
# Option A: Clone godot-cpp
# git clone https://github.com/godotengine/godot-cpp.git /tmp/godot-cpp
# Look for extension_api.json in the gdextension/ directory

# Option B: Download from Godot releases
# https://github.com/godotengine/godot/releases

[USER SAYS:
    There is a difference between cloning the whole repo and downloading. When you say cloning, this means that you\'re interested in all the history, meaning what happened to the file as the repository has evolved. I don\'t think its possible with git because its not designed to do so. I\'ll be glad to be proven otherwise though.

    If you want, however, to \"just download\" the last \"snapshot\" of the file (which I assume what you really want), then you have a couple of options:

    Use git archive command:

    git archive --remote=ssh://<address>/repo.git <BranchName|HEAD> /some/path/file.txt | tar -xO /some/path/file.txt > /tmp/file.txt]

# 3. Generate bindings
./mill generateBindings /path/to/extension_api.json
# This creates ~800+ Scala files in bindings/src/godot/

# 4. Review generated code
ls bindings/src/godot/
cat bindings/src/godot/Node.scala

# 5. Compile bindings (will fail until runtime FFI is complete)
./mill bindings.compile

# 6. Build examples (not ready yet)
# ./mill examples.simple2d.buildExtension
```

### Testing

```bash
# Run bindings tests (when implemented)
./mill bindings.test

# Manual testing
./mill generateBindings /path/to/extension_api.json
# Check that files are generated
ls -l bindings/src/godot/ | wc -l  # Should show ~800 files
# Inspect a generated class
cat bindings/src/godot/CharacterBody2D.scala
```

## Development Status

**Core Generator Complete** - The code generator is fully implemented and can generate Scala Native bindings from extension_api.json.

✅ **Completed**:
- Parser for extension_api.json
- Code generation infrastructure
- Class, method, property, signal, and enum generators
- Type mapping system (Godot → Scala Native)

⚠️ **In Progress**:
- Runtime FFI bindings (minimal implementation)
- Method body implementation (currently stubbed)
- Builtin types generator

⏸️ **Not Started**:
- Virtual method callbacks
- Complete core runtime types
- Memory management system
- Example projects

See [CLAUDE.md](CLAUDE.md) for detailed architecture and [NEXT_STEPS.md](NEXT_STEPS.md) for the development roadmap.

## Contributing

TBD

## License

TBD

## Acknowledgments

- Inspired by [SwiftGodot](https://github.com/migueldeicaza/SwiftGodot) by Miguel de Icaza
- Built on [Scala Native](https://scala-native.org/)
- Uses [Mill](https://mill-build.com/) build tool
