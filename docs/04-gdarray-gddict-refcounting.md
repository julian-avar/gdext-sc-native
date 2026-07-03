# GdArray / GdDict Refcounting — Use-After-Free Risk

Godot's `Array` and `Dictionary` are reference-counted heap types. Their 8-byte handle
is an opaque pointer to internally refcounted data. The current implementation copies
this handle with `memcpy` **without adjusting the refcount**, which creates two compound
bugs.

## The Broken Pattern

```mermaid
flowchart TB
    subgraph "ToVariant.write (GdArray to Variant)"
        SRC("GdArray.handle -- 8-byte opaque pointer")
        DST("Variant data section bytes 8-15")
        MEM1["memcpy(dst+8, src.handle, 8)"]
        MISSING1["MISSING: refcount increment on the Godot Array"]
        SRC --> MEM1 --> DST
        MEM1 -.-> MISSING1
    end

    subgraph "FromVariant.read (Variant to GdArray)"
        SRC2("Variant data section bytes 8-15")
        MAL["malloc(8) for new handle"]
        MEM2["memcpy(malloc'd, src+8, 8)"]
        MISSING2["MISSING: refcount increment on the Godot Array"]
        MISSING3["MISSING: variant_destroy on source Variant"]
        SRC2 --> MEM2 --> MAL
        MEM2 -.-> MISSING2
        MEM2 -.-> MISSING3
    end

    subgraph "Result"
        BUG1["Bug 1: Source Variant not destroyed -- refcount permanently +1 -- LEAK"]
        BUG2["Bug 2: No refcount increment on copy -- if Variant were destroyed, handle could become stale -- USE-AFTER-FREE"]
    end

    MISSING3 --> BUG1
    MISSING2 --> BUG2
```

## Why It's Critical

If you fix the `variant_destroy` leak (Bug 1), you immediately trigger Bug 2:

```mermaid
flowchart LR
    subgraph "Scenario: Fix variant_destroy"
        A["Variant holds Array with refcount=2"]
        B["FromVariant reads handle copy -- refcount still 2"]
        C["variant_destroy decrements -- refcount=1"]
        D["GdArray handle valid"]
        A --> B --> C --> D
    end

    subgraph "Scenario: Variant was the only ref"
        A2["Variant holds Array with refcount=1"]
        B2["FromVariant reads handle copy -- no refcount increment"]
        C2["variant_destroy decrements -- refcount=0"]
        D2["Godot frees the Array data"]
        E2["GdArray.handle now stale -- USE-AFTER-FREE"]
        A2 --> B2 --> C2 --> D2 --> E2
    end
```

## Correct Fix

Use Godot's copy constructors for Array and Dictionary, which properly increment
the refcount:

```mermaid
flowchart TB
    subgraph "Fixed FromVariant"
        SRC("Variant data section")
        CTOR["variant_get_ptr_constructor (type=Array, index=1 for copy)"]
        MAL2["malloc(8) for new handle"]
        COPY["Copy constructor call dest=malloc'd, src=variant+8 -- refcount++"]
        DESTROY["variant_destroy(src) -- refcount--"]
        OK["Net: refcount unchanged -- handle is a proper owned copy"]
        SRC --> CTOR --> COPY
        MAL2 --> COPY
        COPY --> DESTROY --> OK
    end
```

## `destroy()` Double-Free Bug

`GdArray.destroy()` and `GdDict.destroy()` call `free(handle)` unconditionally:

```scala
// GdArray.scala:183-185
def destroy(): Unit = if handle != null then
    GdxApi.destroyArray(handle)  // Godot destructor
    free(handle)                   // free the malloc'd buffer
```

This is correct **only if** the handle was allocated by `malloc` (i.e., created via `GdArray[A]()`
or `FromVariant.read`). If the GdArray was created via `fromHandle` (wrapping an engine-owned
handle), calling `free` is **undefined behavior**.

**Fix:** Track whether the handle is owned (malloc'd) or borrowed (fromHandle).

## `foreach` Variant Leak

Both `GdArray.foreach` and `GdDict.foreach` call `apply(i)` per element, which fills and leaks
a Variant for each access:

```mermaid
flowchart LR
    FOREACH["gdArray.foreach { elem => ... }"]
    APPLY["apply(i) -- creates Variant buffer -- reads element -- NEVER destroys Variant"]
    LEAK["24 bytes x N elements leaked per iteration"]
    FOREACH --> APPLY --> LEAK
```

For an array of 1000 elements iterated once per frame at 60fps for 60 seconds:
1000 × 24 × 60 × 60 = **86 MB leaked**.

## Files

- `gdext/core/src/gdext/core/GdArray.scala` — `ToVariant`, `FromVariant`, `destroy`, `foreach`
- `gdext/core/src/gdext/core/GdDict.scala` — `ToVariant`, `FromVariant`, `destroy`, `foreach`
