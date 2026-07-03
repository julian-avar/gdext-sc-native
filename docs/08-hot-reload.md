# Hot-Reload Lifecycle

Godot 4.3+ supports `ClassCreationInfo3` with an `is_runtime` flag and a
`recreate_instance_func` callback. This enables recompiling the `.so` and swapping it
at runtime without restarting the editor.

## Hot-Reload Sequence

```mermaid
sequenceDiagram
    participant Dev as Developer
    participant Godot as Godot Editor
    participant Old as Old .so
    participant New as New .so

    Dev->>Godot: Edit source code
    Godot->>Old: Extension running
    Dev->>Dev: mill examples.rigid_body.buildExtension
    Dev->>Godot: Replace libgodot-scala-native.so
    Godot->>New: Load new .so
    New->>New: godot_scala_init() called
    New->>New: Register classes again
    New->>New: GodotEntry.init() sets callbacks
    Godot->>Old: deinit(Editor)
    Old->>Old: unregisterAll(Editor)
    Godot->>Old: deinit(Scene)
    Old->>Old: unregisterAll(Scene)
    Godot->>Godot: For each node with old extension instance:
    Godot->>New: recreate_instance_func(userdata, godotPtr)
    New->>New: factory() → new Scala instance
    New->>New: instanceMap += instancePtr → instance
    New->>New: godotPtrMap += godotPtr → instance
    New->>New: instanceToGodotPtr += instancePtr → godotPtr
    New->>New: GdxApi.setInstance(godotPtr, classNameBuf, instancePtr)
    New-->>Godot: return instancePtr
    Godot->>Godot: Node now wired to new Scala instance
    Godot->>New: init(Scene) → ClassRegistrar.register(Scene)
```

## is_runtime Flag

| Class type | `is_runtime` | Effect |
|-----------|-------------|--------|
| Node subclass | `true` | Editor treats as placeholder — no `_ready`/`_process` while editing |
| Resource subclass | `false` | Real instance required in editor |
| Object subclass | `false` | Real instance required in editor |
| `@tool` class | `false` | Must run in editor unconditionally |

## Init Level Selection

```mermaid
flowchart LR
    subgraph "Registration level"
        TOOL{"Has @tool annotation?"}
        SCENE["initLevel = Scene"]
        EDITOR["initLevel = Editor"]
    end

    subgraph "Godot calls init_callback"
        INIT_EDITOR["init_callback(Editor)\n→ ClassRegistrar.register(Editor)\n→ registers @tool classes"]
        INIT_SCENE["init_callback(Scene)\n→ ClassRegistrar.register(Scene)\n→ registers game classes"]
    end

    TOOL -->|no| SCENE
    TOOL -->|yes| EDITOR
    SCENE --> INIT_SCENE
    EDITOR --> INIT_EDITOR
```

## EditorPlugin Auto-Activation

Classes whose parent is `EditorPlugin` are automatically activated:

```mermaid
flowchart LR
    subgraph "During ClassRegistrar.register"
        CHECK{"parentName == 'EditorPlugin'\n&& initLevel == Editor"}
        ADD["GdxApi.editorAddPlugin(buf)\neditorPluginBufs += buf"]
        REMOVE["On deinit(Editor):\neditorRemovePlugin for each buf"]
    end

    CHECK -->|yes| ADD
    CHECK -->|no| SKIP[skip]
```

## Unregistration on Deinit

```mermaid
flowchart TB
    subgraph "deinit(Editor)"
        EP["editorPluginBufs.foreach:\neditorRemovePlugin(buf)"]
        UNREG["partition registeredClassBufs\nby initLevel == Editor\nfor each: unregisterClass"]
        KEEP["keep non-Editor registrations"]
    end

    subgraph "deinit(Scene)"
        UNREG2["partition by initLevel == Scene\nfor each: unregisterClass"]
        RESET["if registeredClassBufs empty:\nreset ALL maps to empty"]
    end

    deinit_Editor --> EP --> UNREG --> KEEP
    deinit_Scene --> UNREG2 --> RESET
```

## Known Race Condition

On hot-reload, Godot may call the old `.so`'s `deinit` after the new `.so`'s `init`
has started registering classes. The `unregisterAll` checks `getClassTag` before
unregistering to guard against double-unregistration:

```scala
// ClassRegistrar.scala:109
if GdxApi.getClassTag(buf) != null then GdxApi.unregisterClass(gdxLibrary, buf)
```

## Files

- `gdext/core/src/gdext/core/GodotEntry.scala` — init/deinit callbacks with level dispatch
- `gdext/core/src/gdext/core/ClassRegistrar.scala` — `register(level)`, `unregisterAll(level)`, `recreateFn`
- `gdext/core/src/gdext/core/Register.scala` — `isTool`, `isRuntime`, `initLevel` macro logic
