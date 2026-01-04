# Simple 2D Example

A minimal example demonstrating godot.sc usage in a 2D game.

## Building

```bash
mill examples.simple2d.buildExtension
```

This will compile the Scala code to a native shared library that Godot can load.

## Running

1. Open this directory in Godot Engine
2. The extension should be automatically loaded via the `.gdextension` file
3. Your Scala classes will be available in the editor

## Project Structure

```
simple2d/
├── src/              # Scala source code
├── extension/        # Generated extension files
│   ├── bin/         # Compiled native libraries
│   └── simple2d.gdextension
├── project.godot     # Godot project file
└── README.md
```
