# Variant Lifecycle — The Root Cause of All Leaks

A Godot `Variant` is a 24-byte tagged union. It is the universal marshalling format for
signals, properties, method arguments, and collection elements. **Every read from a Variant
must be paired with a `variant_destroy` call**, or the Variant's internal reference-counted
data leaks.

## Variant Memory Layout

```mermaid
flowchart LR
    subgraph "24-byte Variant buffer"
        T["bytes 0-3 -- type tag (uint32)"]
        P["bytes 4-7 -- padding"]
        D["bytes 8-23 -- payload (16 bytes)"]
    end

    T -->|"type tag decides how to interpret"| D
    D -->|"value types (Int, Float, Bool, Vector2, etc.)"| INLINE["stored inline -- no allocation"]
    D -->|"reference types (String, Array, Dict, Object, Callable, RID)"| REF["pointer to heap-allocated data with internal refcount"]
```

## The Missing `variant_destroy` Call

```mermaid
flowchart TB
    subgraph "Current (BUGGY)"
        API["GdxApi stores variantDestroyPtr"]
        NEVER["NEVER called in the entire codebase"]
        READ["FromVariant.read(src) or Variant.readString(src)"]
        LEAK["Variant's refcounted data stays pinned forever -- LEAK"]
        API --> NEVER
        READ --> LEAK
    end

    subgraph "Correct"
        API2["GdxApi stores variantDestroyPtr"]
        CALL["variant_destroy(buf) called after every read"]
        READ2["FromVariant.read(buf) -> value"]
        CALL2["variant_destroy(buf)"]
        OK["Godot decrements refcount -- internal data freed when 0"]
        API2 --> CALL
        READ2 --> CALL2 --> OK
    end
```

## What Leaks

Every call site that reads from a Variant without destroying it leaks for
reference-counted types:

| Call site | File | Type leaked |
|-----------|------|-------------|
| `GdArray.apply(i)` | `GdArray.scala:59` | Array element (Variant) |
| `GdArray.indexOf(v)` | `GdArray.scala:116` | Variant buffer |
| `GdArray.contains(v)` | `GdArray.scala:101` | Variant buffer |
| `GdDict.get(k)` | `GdDict.scala:73-75` | Dict value (Variant) |
| `GdDict.has(k)` | `GdDict.scala:53` | Variant buffer |
| `GdDict.foreach` | `GdDict.scala:131-146` | N Variants per iteration |
| `GdArray.foreach` | `GdArray.scala:160-166` | N Variants per iteration |
| `Variant.readString` | `PropertyDescriptor.scala:141` | String Variant |
| `FromVariant.read` for String | `VariantTypeclasses.scala:90` | String Variant |
| `fromVariantGdArray.read` | `GdArray.scala:219-226` | Array handle (refcount) |

String operations in `Variant.readString` DO call `destroyGodotString` on the temporary
string buffer, but do NOT call `variant_destroy` on the source Variant `p`.

## String Variant Lifetime

```mermaid
flowchart LR
    subgraph "Variant.writeString"
        SB1["stackalloc 8 bytes"]
        IG1["initGodotString from UTF-8 CString"]
        BV1["buildStringVariant copies into Variant"]
        DG1["destroyGodotString frees temp"]
        SB1 --> IG1 --> BV1 --> DG1
    end

    subgraph "Variant.readString"
        SB2["stackalloc 8 bytes"]
        ES["extractStringFromVariant"]
        GS["godotStringToScala returns Scala String"]
        DG2["destroyGodotString frees temp"]
        MISSING["MISSING: variant_destroy on the source Variant"]
        SB2 --> ES --> GS --> DG2
        GS -.-> MISSING
    end
```

## Files

- `gdext/core/src/com/julian-avar/gdext/core/GdxApi.scala:213` — `variantDestroyPtr` stored but never called
- `gdext/core/src/com/julian-avar/gdext/core/PropertyDescriptor.scala:141-149` — `Variant.readString`
- `gdext/core/src/com/julian-avar/gdext/core/VariantTypeclasses.scala` — `FromVariant` implementations
