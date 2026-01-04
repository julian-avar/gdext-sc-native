# godot.sc

Scala Native bindings for Godot Engine via GDExtension.

Write Godot games in Scala with native performance and no GC pauses!

## Project Structure

```
godot.sc/
├── runtime/          # Core handcoded types and GDExtension bindings
├── generator/        # Code generator (parses extension_api.json)
├── bindings/         # Generated Godot API bindings
├── examples/         # Example projects
│   └── simple2d/    # Simple 2D game example
├── build.sc          # Mill build definition
├── CLAUDE.md         # Detailed architecture documentation
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
# Compile the runtime
./mill runtime.compile

# Compile the generator
./mill generator.compile

# Generate bindings (you need extension_api.json from Godot)
./mill generateBindings /path/to/extension_api.json

# Compile bindings
./mill bindings.compile

# Build an example
./mill examples.simple2d.buildExtension
```

### Testing

```bash
# Run runtime tests
./mill runtime.test

# Run generator tests
./mill generator.test
```

## Development Status

**Early development phase** - Currently setting up the project structure and implementing core components.

See [CLAUDE.md](CLAUDE.md) for detailed architecture documentation and development roadmap.

## Contributing

TBD

## License

TBD

## Acknowledgments

- Inspired by [SwiftGodot](https://github.com/migueldeicaza/SwiftGodot) by Miguel de Icaza
- Built on [Scala Native](https://scala-native.org/)
- Uses [Mill](https://mill-build.com/) build tool
