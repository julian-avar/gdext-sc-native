# Generated Bindings

This directory contains auto-generated Scala Native bindings for the Godot Engine API.

**Do not edit files in this directory manually.** They will be overwritten.

## Generating Bindings

To generate the bindings, you need Godot's `extension_api.json` file:

1. Download or build Godot Engine
2. Extract `extension_api.json` from the Godot source or binary
3. Run the generator:

```bash
mill bindings.generate /path/to/extension_api.json
```

Or use the workspace-level command:

```bash
mill generateBindings /path/to/extension_api.json
```

## Generated Structure

The generator will create Scala files matching Godot's class hierarchy:

```
src/godot/
├── Node.scala
├── Node2D.scala
├── Node3D.scala
├── CharacterBody2D.scala
├── Resource.scala
├── Texture2D.scala
└── ... (all Godot classes)
```

Each generated file will include:
- Class definition with proper inheritance
- Method bindings
- Property accessors
- Signal definitions
- Virtual method hooks
