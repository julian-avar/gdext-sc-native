# Memory Management Overview

The binding runs **two independent memory managers** in parallel. Understanding both is
required for correct resource management.

---

## 1. Two Parallel Ownership Systems

Every Godot object has a Scala wrapper (tracked by Scala Native's Immix GC) and a native
engine object (tracked by Godot's refcount / scene tree):

```
Scala GC world                  Godot refcount world
────────────────────            ──────────────────────
GodotObject (Scala wrapper)     Native engine object
  var ptr: Ptr[Byte] ─────────→  refcount ≥ 1
  (tracked by Immix GC)           (tracked by Godot)
```

**Key rule:** Scala GC manages the wrapper; you manage the engine object.

- The Immix GC is **non-moving** — raw pointers held by Godot callbacks into Scala
  memory remain valid indefinitely.
- You never `free()` a Scala object. But you **must** explicitly free/unref/destroy
  engine objects.

---

## 2. Two Lifetime Regimes

| Regime | `isRefCounted` | Free method | Examples |
|--------|---------------|-------------|----------|
| Manual | `false` | `Gd[T].free()` → `object_destroy` | `Node`, `Node2D`, `Object` |
| Counted | `true` | `Gd[T].unref()` → `unreference()` | `Resource`, `AudioStream`, `Texture` |

`Gd[T].close()` (for `AutoCloseable`/`Using`) dispatches correctly based on `isRefCounted`:
```scala
def close(): Unit = if cls.isRefCounted then unref() else free()
```

---

## 3. Gd[T] Lifecycle

### Acquiring
| Method | Behavior |
|--------|----------|
| `Gd.newInstance[T]` | `constructObject` + `init_ref` (RefCounted only) |
| `Gd.fromHandle[T](ptr)` | wraps existing pointer, no ownership change |
| `getNode[T]("path")` | wraps existing scene tree node, no ownership change |
| `nullOf[T]` | null sentinel |

### Releasing
- **Manual (Node/Object):** `free()` calls `object_destroy`. Nodes added to the scene
  tree are owned by their parent — do **not** free those.
- **Counted (Resource):** `unref()` drops one reference. `Using(gd) { ... }` for
  scoped lifetime.
- **Engine returns:** Generated engine methods that return `RefCounted` subtypes
  automatically call `reference()` on the returned object (via `retSetup` in the
  generator). No manual `.reference()` needed.

### Null Safety
```scala
var target: Player = nullOf   // null sentinel
target.isNull                 // true
target.get                    // throws NullPointerException
target.free()                 // no-op
```

---

## 4. Value Types in the High-Level API

### How Value Types Work

Built-in types (`Vector2`, `Color`, `Transform3D`, etc.) are opaque C struct pointers.
They live on the stack or in a `Zone`. The user does not manage their memory:

```scala
val dir = Vector2(1f, 0f)          // stack-allocated, no cleanup needed
val scaled = dir * speed.toFloat   // new stack copy
```

Some engine methods that return value types require a `Zone`:
```scala
val pos = Zone { getPosition() }
```

Every virtual dispatch creates a Zone automatically, so inside `_ready`/`_process`
you call zone-requiring methods directly.

### GdArray and GdDict — Value Type Semantics

`GdArray[A]` and `GdDict[K,V]` behave as value types in the high-level API. Their
8-byte Godot handle is passed inline (no heap allocation for the wrapper). The Godot
Array/Dictionary backing data is internally reference-counted:

```
val arr = GdArray[Int]()    // handle on stack, Godot allocates backing data (refcount=1)
arr.append(42)
val copy = arr              // copy the 8-byte handle (backing data shared)
```

**No manual cleanup needed for the high-level API.** Godot's internal refcounting
handles the backing data. Exported properties retain their handles for the extension
lifetime.

**Trade-off:** Without automatic C++-style destructors, Godot backing data for arrays
that go out of scope in tight loops is never freed. For game-code patterns (a handful
of arrays, not created in hot loops), this is invisible. If you hit this, use the
low-level API (future) with explicit `alloc()`/`free()`.

---

## 5. GdArray/GdDict Current Implementation

The current implementation already handles refcounting correctly:

- **`FromVariant`:** Uses Godot's copy constructor (`variant_get_ptr_constructor`
  with index 1) to increment the backing refcount when reading from a Variant.
- **`ToVariant`:** Copies the 8-byte handle into the Variant data section.
- **`apply(i)` / `update(i, v)`:** Creates and destroys intermediate Variants on the
  stack (no leak).
- **No `destroy()` method:** This is intentional — GdArray is a value type. The
  low-level API later will expose explicit lifecycle management.

### The leak trade-off visualized

```
High-level (current):                   Low-level (future):
─────────────────────                   ────────────────────
val arr = GdArray[Int]()               val arr = GdArray.alloc[Int]()
arr.append(42)                         arr.append(42)
// backing data lives until             arr.free()
// process exit or GC finalizer
```

For 99% of game code the high-level leak is benign:
- A few arrays created at startup → leak once, never grows
- Exported arrays → extension-lifetime handle, never leaks
- Arrays in `_process(delta)` → same array reused, no growth

---

## 6. Engine-Generated Method Returns

Generated engine class wrappers return typed values. For RefCounted subtypes, the
generator emits a `reference()` call on the returned pointer (`retSetup` in
`WrappersGenerator.scala`). The returned `Gd[T]` holds an owned reference:

```scala
val tex: Gd[Texture2D] = ResourceLoader.singleton.load("res://icon.png")
// `load` returns Gd[Texture2D] — already referenced, no extra work
tex.unref() // drop your reference when done
```

---

## 7. Callables and Callback Lifetime

`CallableLambda` wraps a Scala closure as a Godot `Callable`. The closure is kept alive
by `CallbackRegistry` (a `mutable.Map[Int, AnyCallback]`). The map entry persists until
explicitly disconnected:

```scala
val token = button.connect("pressed") { () => doAction() }
// ...
disconnect(token)  // removes the callback entry
```

**Without disconnect**, the callback entry and its captured state leak. Always disconnect
when the lifetime of the signal connection does not match the lifetime of the object.

---

## 8. Zone System

Value-type builtins (`Vector2`, `Vector3`, `Color`, etc.) are opaque C structs.
Methods that return them need a temporary buffer:

- **Explicit:** `Zone { getPosition() }`
- **Automatic:** Every virtual dispatch creates a Zone, so `_ready`/`_process` overrides
  can call zone-requiring methods without explicit `Zone { }` blocks.

```scala
@gdclass class PlayerSc extends CharacterBody2D:
    override def _physicsProcess(delta: Double): Unit =
        val dir = Input.getVector("left", "right", "up", "down")
        velocity = dir * speed.toFloat
        moveAndSlide()
```

---

## 9. Known Limitations

| Issue | Severity | Context |
|-------|----------|---------|
| GdArray/GdDict backing data never freed in high-level API | Low (by design) | Value type semantics; low-level API provides `free()` |
| `FromVariant[RID]` heap-allocates 8 bytes per read | Low | Rarely used; fix with value-type RID wrapper |
| `CallbackRegistry` entries persist without disconnect | Medium | Always disconnect; fix for v0.2 |
| Signal arity limited to 3 | Low | Rare in practice; fix for C# parity |
| No automatic destructors for RefCounted handles | Low (by design) | Explicit `unref()` required |

---

## 10. Files Reference

| File | Role |
|------|------|
| `gdext/core/src/gdext/core/Gd.scala` | `Gd[T]` handle: `free()`, `unref()`, `close()`, `cast` |
| `gdext/core/src/gdext/core/GodotObject.scala` | Base class: `ptr`, `connect`/`disconnect` |
| `gdext/core/src/gdext/core/GdArray.scala` | Typed Array value wrapper |
| `gdext/core/src/gdext/core/GdDict.scala` | Typed Dictionary value wrapper |
| `gdext/core/src/gdext/core/GdxApi.scala` | FFI function pointers, `CallbackRegistry`, `variantDestroyPtr` |
| `gdext/core/src/gdext/core/VariantTypeclasses.scala` | `ToVariant`/`FromVariant` instances |
| `gdext/core/src/gdext/core/PropertyDescriptor.scala` | Property metadata + Variant helpers |
| `gdext/core/src/gdext/core/PackedArrays.scala` | Packed array extension methods |
| `gdext/core/src/gdext/core/ClassRegistrar.scala` | Instance maps, create/free/recreate |
| `gdext/core/src/gdext/core/Callables.scala` | `CallableLambda` factory |
| `gdext/core/src/gdext/core/Ptrcall.scala` | Ptrcall dispatchers (stack-allocated args/ret) |

---

## 11. Future Low-Level API

The low-level API (planned) will expose:

- `GdArray.alloc[A]()` / `GdArray.free(arr)` — explicit lifecycle
- `GdDict.alloc[K,V]()` / `GdDict.free(dict)` — explicit lifecycle
- `GdxApi` as a user-facing object — direct FFI access
- `Zone.alloc[T](size)` — manual zone allocation
- `VariantBuilder` — manual Variant construction/destruction

This is for library authors and performance-critical code. Game developers use the
high-level API and never think about GdArray memory.
