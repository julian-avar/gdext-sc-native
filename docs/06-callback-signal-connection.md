# CallbackRegistry, Signal Connection, and ConnectionToken

Signal connections route through a trampoline-based `CallbackRegistry` that maps
integer IDs to Scala closures. Godot calls the trampoline C function pointer, which
looks up the closure by ID and invokes it.

## Signal Connection Flow

```mermaid
flowchart TB
    subgraph "User code"
        CONNECT["button.connect('pressed') { () =>\n  doAction()\n}"]
        TOKEN["returns ConnectionToken\n(signal name + udBuf)"]
    end

    subgraph "CallbackRegistry"
        REG["register(callback)\nallocates id"]
        NEXT["nextId += 1\natomicity? NO — data race"]
        MAP["callbacks(id) = callback\nmutable.Map — NOT thread-safe"]
    end

    subgraph "Godot side"
        UDBUF["malloc(4)\nstores id as Ptr[Int]"]
        TRAMP["CallbackRegistry.trampoline\nshared CFuncPtr"]
        CS["GdxApi.connectSignal\n(ptr, name, trampoline, udBuf)"]
    end

    CONNECT --> REG
    REG --> NEXT
    REG --> MAP
    REG --> UDBUF
    REG --> CS
    CONNECT --> TOKEN
    MAP --> TRAMP
    UDBUF --> TRAMP

    subgraph "Dispatch"
        SIGNAL[Godot emits signal]
        TRAMP2["trampoline(userdata)\nreads id from udBuf"]
        LOOKUP["looks up callbacks(id)"]
        INVOKE["invokes the Scala closure"]
        SIGNAL --> TRAMP2 --> LOOKUP --> INVOKE
    end
```

## Two Leaks

### Leak 1: Unbounded `callbacks` Map Growth

Entries are **never removed** from `callbacks`. Even after `disconnect`, the callback
closure and its ID remain in the map:

```mermaid
flowchart LR
    CONNECT2["connect → register(id, cb)"]
    DISCONNECT["disconnect → frees udBuf\nbut does NOT remove from map"]
    LEAK["callback entry persists\nfor the extension lifetime"]
    CONNECT2 --> DISCONNECT --> LEAK
```

### Leak 2: Orphaned ConnectionToken

If the user drops the `ConnectionToken` without calling `disconnect`:

```mermaid
flowchart LR
    CONNECT3["connect → malloc(4) for udBuf"]
    DROP["user drops ConnectionToken"]
    LEAK2["4 bytes leaked\ncallback ID orphaned in map"]
    CONNECT3 --> DROP --> LEAK2
```

## `CallableLambda` — The Same Pattern

`CallableLambda` wraps Scala closures as Godot `Callable` objects for engine APIs that
expect a raw `Callable` argument (e.g., `Tween.tweenCallback`):

```mermaid
flowchart LR
    subgraph "CallableLambda.apply { f }"
        REG2["CallbackRegistry.register(cb)\nsame unbounded growth"]
        UDBUF2["malloc(4) for id"]
        CBUF["malloc(16) for Callable buffer"]
        BUILD["GdxApi.buildCallable\nfills Callable struct"]
        REG2 --> UDBUF2 --> CBUF --> BUILD
    end
```

These callbacks also leak in the registry map.

## Thread Safety Issues

| Data structure | Access pattern | Problem |
|---------------|---------------|---------|
| `callbacks: mutable.Map[Int, AnyCallback]` | read (trampoline) + write (register) | No synchronization |
| `nextId: Int` | `nextId += 1` — read-modify-write | Data race — two Godot threads can get same ID |

## ConnectionToken

```scala
final class ConnectionToken private[core] (
    val signal: String,
    private[core] val udBuf: Ptr[Byte]  // heap-allocated, freed in disconnect
)
```

`disconnect` frees `udBuf` but does not remove the callback from `CallbackRegistry`:

```scala
// GodotObject.scala:414-416
def disconnect(token: ConnectionToken): Unit =
    GdxApi.disconnectSignal(ptr, token.signal, token.udBuf)
    free(token.udBuf)  // frees the 4-byte buffer
    // MISSING: CallbackRegistry.remove(token.id)
```

## Typed Signal Handles (Signal0-Signal8)

Generated signal handles provide type-safe emission and connection:

```mermaid
flowchart LR
    subgraph "Generated handles"
        EXT["extension (self: PlayerSc)\n  def died: Signal0\n  def healthChanged: Signal2[Int, Int]"]
    end

    subgraph "Emit"
        EXT2["this.healthChanged.emitSignal(oldHp, newHp)"]
        API["GdxApi.emitSignalArgs2\n(ptr, name, va, vb)"]
    end

    subgraph "Connect"
        EXT3["this.healthChanged.connect { (old, new) =>\n  updateHp(new)\n}"]
        EXT3 -->|delegates to| CONNECT["GodotObject.connect[A,B]"]
    end

    EXT --> EXT2 --> API
    EXT --> EXT3
```

## Files

- `gdext/core/src/com/julian-avar/gdext/core/GdxApi.scala:182-185` — `CallbackRegistry` (map + nextId)
- `gdext/core/src/com/julian-avar/gdext/core/GodotObject.scala` — `connect` overloads, `disconnect`, `ConnectionToken`
- `gdext/core/src/com/julian-avar/gdext/core/SignalHandles.scala` — `Signal0` through `Signal8`
- `gdext/core/src/com/julian-avar/gdext/core/Callables.scala` — `CallableLambda` factory
