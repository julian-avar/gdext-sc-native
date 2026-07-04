# Two Parallel Ownership Systems

The binding maintains **two independent memory managers** running in parallel. Every Godot object has
both a Scala wrapper (tracked by Scala Native's Immix GC) and a native engine object (tracked by
Godot's refcount / scene tree).

```mermaid
flowchart TB
    subgraph "Scala GC (Immix -- non-moving)"
        W("GodotObject subclass (Scala wrapper)")
        GC["Immix GC manages wrapper lifetime"]
        W -.->|tracked by| GC
    end

    subgraph "Godot Memory (C engine)"
        N("Native engine object (ptr: Ptr[Byte])")
        RT["RefCount / SceneTree manages native lifetime"]
        N -.->|tracked by| RT
    end

    W -- "var ptr: Ptr[Byte]" --> N

    subgraph "Key properties"
        NM["Non-moving GC: raw pointers held by Godot remain valid forever"]
        PAR["Parallel ownership: wrapper can outlive engine or vice versa"]
    end
```

## Two Lifetime Regimes

Selection is per-class via `GodotClass[T].isRefCounted`:

```mermaid
flowchart LR
    subgraph "Manual (Object/Node)"
        O[Node/Object\nisRefCounted = false]
        M1["free() → object_destroy"]
        M2["Scene tree owns nodes\nadded via addChild()"]
        M3["DO NOT free nodes\nowned by the tree"]
    end

    subgraph "Counted (RefCounted)"
        R[Resource/AudioStream/Texture\nisRefCounted = true]
        C1["newInstance → init_ref\n(first reference)"]
        C2["unref() → unreference()\n(drop one ref)"]
        C3["AutoCloseable →\nUsing(res) { ... }"]
    end
```

## Identity Preservation

When Godot calls back into Scala (virtual dispatch, property get/set), the `ClassRegistrar`
maintains a `godotPtrMap` so the **same Scala instance** is returned for a given engine pointer:

```mermaid
flowchart LR
    subgraph "ClassRegistrar maps"
        IM[instanceMap\ninstancePtr → Scala object]
        GPM[godotPtrMap\ngodotPtr → Scala object]
        ITG[instanceToGodotPtr\ninstancePtr → godotPtr]
    end

    CREATE[create_instance_func] -->|malloc instancePtr| IM
    CREATE -->|stores godotPtr| GPM
    VIRTUAL[get_virtual / set / get / method] -->|lookup godotPtr| GPM
    GPM -->|returns live Scala object| VIRTUAL
    FREE[free_instance_func] -->|evicts from| IM & GPM & ITG
```

## Key Rule

> **Scala GC manages the wrapper; you manage the engine object.**
>
> The wrapper `GodotObject` subclass holds a `var ptr: Ptr[Byte]` pointing to the native
> engine object. The Scala GC may collect the wrapper at any time (it is non-moving, so raw
> pointers are safe). But the native engine object persists until you explicitly call `free()`,
> `unref()`, or the scene tree destroys it.

## Files

- `gdext/core/src/com/julianavar/gdext/core/Gd.scala` — `Gd[T]` handle type
- `gdext/core/src/com/julianavar/gdext/core/GodotObject.scala` — base class with `ptr` field
- `gdext/core/src/com/julianavar/gdext/core/GodotClass.scala` — `isRefCounted` flag
- `gdext/core/src/com/julianavar/gdext/core/GdClassRegistry.scala` — registration storage
- `gdext/core/src/com/julianavar/gdext/core/ClassRegistrar.scala` — `instanceMap`, `godotPtrMap`
