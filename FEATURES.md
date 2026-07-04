# Features

Godot 4.x GDExtension binding for Scala Native. Targets C# parity for the scripting surface.
API is unstable — breaking changes expected.

State: **Amoeba** — core architecture is solid, examples may not compile cleanly yet.

---

## Extension Classes

Define extension classes with a single annotation. The `Register.auto` macro scans the class
at compile time and emits all registration boilerplate.

```scala
@gdclass class SpinningCube extends Node3D:
    override def ready(): Unit = println("Hello from Scala!")
```

- `@gdclass` — marks the class; macro generates `GdClassRegistry.register(...)` automatically
- Class appears in Godot's **ClassDB**, **scene tree**, and the **Create Node** dialog
- Extends any engine class (`Node`, `Node2D`, `Node3D`, `CharacterBody2D`, `RigidBody3D`, etc.)
- Custom class extends custom class (e.g. `class Warrior extends Player`)
- `@tool` — registers the class at **Editor** init level (runs in editor, not just at runtime)

---

## Virtual Methods

Override any Godot virtual method — the macro auto-detects overrides and registers them:

```scala
@gdclass class PlayerSc extends CharacterBody2D:
    override def ready(): Unit = println("ready")
    override def process(delta: Double): Unit = ()
    override def physicsProcess(delta: Double): Unit = moveAndSlide()
    override def input(event: InputEvent): Unit = ()
    override def unhandledInput(event: InputEvent): Unit = ()
    override def enterTree(): Unit = ()
    override def exitTree(): Unit = ()
```

All lifecycle virtuals from the generated `{Class}Virtuals` stubs are detected automatically.
No explicit virtual registration call needed.

**Note:** virtual methods do not carry `(using Zone)` on their signatures anymore —
the dispatch trampoline wraps each call in a `Zone`, so any zone-requiring method called
from within a virtual override works automatically.

---

## Exported Properties

Export fields to the **Inspector** panel with `@gdexport`. The macro generates the
getter/setter trampolines and `PropertyInfo` registration automatically.

```scala
@gdclass class ShowcaseSc(
  @gdexport(ExportHint.range(1, 200, 1)) var damage: Int = 10,
  @gdexport var speed: Float = 400f
) extends Node2D:
  @gdexport var notes: String = "edit me"
  @gdexport var itemResource: Tres[Resource] = Tres.empty
  @gdexport var tags: GdArray[String] = GdArray()
```

- Works on **primary constructor params** and **body `var` fields**
- Optional `ExportHint` argument overrides the default inspector widget
- `DefaultValue[A]` fills missing defaults when Godot constructs instances with no args

### Export Hints

```scala
@gdexport(ExportHint.range(0, 100, 1))            var health: Int = 100
@gdexport(ExportHint.range(0.0, 1.0, 0.01))       var opacity: Double = 1.0
@gdexport(ExportHint.multiline)                   var bio: String = ""
@gdexport(ExportHint.colorNoAlpha)                var tint: Color = Color(1f,1f,1f,1f)
@gdexport(ExportHint.file("*.png"))               var icon: String = ""
@gdexport(ExportHint.dir)                         var saveDir: String = ""
@gdexport(ExportHint.resourceType("Texture2D"))   var tex: Tres[Texture2D] = Tres.empty
@gdexport(ExportHint.nodeType("CharacterBody2D")) var target: Gd[CharacterBody2D] = nullOf
@gdexport(ExportHint.enum("A", "B", "C"))         var mode: Int = 0
```

### Inspector Sections

```scala
@export_category("Appearance")
@export_group("Color & Style")
@gdexport(ExportHint.colorNoAlpha) var tint: Color = Color(1f, 0.5f, 0.2f, 1f)

@export_group("Description")
@gdexport(ExportHint.multiline) var notes: String = ""

@export_subgroup("Fine Print")
@gdexport var footnote: String = ""
```

Section markers are interleaved with properties in **field declaration order**.

### Enum Properties

```scala
enum Rarity:
  case Common, Uncommon, Rare, Epic, Legendary

@gdenum enum Direction:
  case North, South, East, West

@gdclass class Item extends Resource:
  @gdexport var rarity: Rarity = Rarity.Common
  @gdexport var facing: Direction = Direction.North
```

Scala enums annotated with `@gdenum` are exported as **ENUM dropdowns** in the inspector.
The macro synthesises the hint string and `ClassIsEnum` usage flag automatically.
Enums without `@gdenum` stay private to Scala.

---

## Methods

Export Scala methods so GDScript, GDExtension, and the engine can call them:

```scala
@gdclass class Enemy extends CharacterBody2D:
  @func def takeDamage(amount: Int): Unit = health -= amount
  @func def getHealth(): Int = health
  @func def getOffset(): Vector2 = Zone { getPosition() }
  @func def scaleDamage(factor: Double): Double = damage * factor
  @func def onBodyEntered(body: PhysicsBody2D): Unit = ()
```

- `@func` — registers the method in ClassDB; callable from GDScript and via `call()`
- camelCase Scala names are exposed as `snake_case` Godot names automatically
- Supports `Int`, `Long`, `Float`, `Double`, `Bool`, `String`, `Vector2/3/4`, `Color`,
  `Rect2`, `Transform2D/3D`, `AABB`, `Quaternion`, `Plane`, `Projection`,
  `Gd[T]`, direct `T <: GodotObject`, `RID`
- Methods returning value types (Vector2, Color, etc.) need a `Zone { }` block;
  see [Zones](#zones) below

---

## Signals

### Declaring signals

```scala
@gdclass class PlayerSc extends CharacterBody2D:
  @signal case class Moved(deltaX: Float, deltaY: Float)
  @signal case class Died()
  @signal case class HealthChanged(old: Int, new_: Int)
```

The macro registers each signal with Godot, including full **typed parameter info**.
Signal signatures appear in the **Node → Signals dock** in the editor.

### Emitting signals

The generated signal handles are **extension methods** on the containing class.
The handle name is the camelCase form of the snake_cased Godot signal name:

```scala
// Correct: use the generated typed handle on `this`
this.moved.emitSignal(velocity.x, velocity.y)
this.died.emitSignal()
this.healthChanged.emitSignal(oldHp, newHp)
```

Generated handle pattern:
```scala
// GeneratedSignalHandles.scala emits:
extension (self: PlayerSc)
  def moved:         Signal2[Float, Float] = Signal2(self, "moved")
  def died:          Signal0               = Signal0(self, "died")
  def healthChanged: Signal2[Int, Int]     = Signal2(self, "health_changed")
```

### Connecting signals

```scala
// Typed — callback args are checked at compile time
val token: ConnectionToken = enemy.connect[Int]("health_changed") { hp =>
  println(s"enemy hp: $hp")
}

val token2 = button.connect("pressed") { () => doAction() }

// Disconnect later
disconnect(token)
disconnect(token2)
```

### Typed signal handles

```scala
val sig: Signal2[Float, Float] = player.moved
sig.emitSignal(dx, dy)
val tok = sig.connect { (dx, dy) => updateHud(dx, dy) }
```

---

## Type System

### `Gd[T]` — typed engine object reference

```scala
val enemy: Gd[Enemy] = getNode[Enemy]("Enemy")
val asChar: Gd[CharacterBody2D] = enemy.cast[CharacterBody2D]
val id: Long = enemy.instanceId
enemy.free()          // Node/Object — manual free
resource.unref()      // RefCounted — drop reference
```

- Two lifetime modes: **manually-managed** (Node/Object → `free()`) and
  **RefCounted** (Resource → `unref()`; implements `AutoCloseable`)
- `Gd.newInstance[T]` calls `init_ref` automatically for RefCounted classes
- `instanceId` — stable Godot object ID
- `cast[U]` — typed downcast via `object_cast_to`
- `nullOf[T]` — null sentinel for nullable `T <: GodotObject` vars

```scala
var target: Player = nullOf   // preferred over null
```

### Property Setters (Generated Setters)

All generated engine wrappers include Scala `_=` setters for properties:

```scala
velocity = dir * speed.toFloat       // calls velocity_=(Vector2)
position = Vector2(x, y)             // calls position_=(Vector2)
modulate = Color(1f, tint, tint, 1f) // calls modulate_=(Color)
tintColor = Color(r, g, b, 1f)       // direct assignment on var
```

The **getter** side may require manual scoping: for example, `position` returns a
stack-allocated copy, and `getPosition()` requires `(using Zone)`. Use the explicit
setter/getter methods when the `_=` setter pattern doesn't fit.

### Math / Builtin Value Types

All 16 Godot builtin value types are available as Scala Native opaque type aliases
over `CStruct` pointers, with operators generated from the Godot API:

`Vector2`, `Vector3`, `Vector4`, `Vector2i`, `Vector3i`, `Vector4i`,
`Color`, `Rect2`, `Rect2i`, `Transform2D`, `Transform3D`, `AABB`,
`Quaternion`, `Plane`, `Projection`, `Basis`

```scala
val dir = Vector2(1f, 0f)
val scaled = dir * speed.toFloat
val sum = dir + Vector2(otherX, otherY)
```

**Operators available:** `+`, `-`, `*`, `/` (scalar and component-wise).
**Accessors:** `x`, `y`, `z`, `w`, `r`, `g`, `b`, `a` (where applicable).

**Missing (planned):** named constants (`ZERO`, `ONE`, `UP`, `DOWN`, `LEFT`, `RIGHT`),
convenience methods (`distanceTo`, `length`, `lengthSquared`, `normalized`, `dot`,
`cross`, `lerp`, `clamp`, `abs`, `sign`, `angle`, `rotated`, `reflect`).
These will be added as extension methods in an upcoming generator update.

### Zones

Value-type builtins (`Vector2`, `Vector3`, `Color`, etc.) are opaque pointer types
wrapping native C structs. When calling generated engine methods that **return** a
value builtin, you may need to wrap the call in a `Zone { }` block or use the
`stackalloc`-based getter:

```scala
// Some methods require explicit Zone:
val pos = Zone { getPosition() }
val dir = Zone { Input.getVector("left", "right", "up", "down") }

// The convenience getter (e.g., `.position`) returns a stack-allocated copy
// that is valid for the current frame. Use it directly in expressions:
velocity = dir * speed.toFloat  // both sides are Vector2, no Zone needed
```

**Every virtual method dispatch creates a Zone automatically**, so inside any
lifecycle override you can call zone-requiring methods directly without a manual
`Zone { }` block:

```scala
@gdclass class PlayerSc extends CharacterBody2D:
    override def physicsProcess(delta: Double): Unit =
        val dir = Input.getVector("left", "right", "up", "down")
        velocity = dir * speed.toFloat
        moveAndSlide()
```

### `RID`

```scala
@func def getRid(): RID = mesh.getRid()
```

Full typeclass support: `PtrArg[RID]`, `PtrRet[RID]`, `ToVariant[RID]`,
`FromVariant[RID]`, `ExportType[RID]`, `DefaultValue[RID]`.

### `GdArray[A]` — typed Godot Array

```scala
@gdexport var tags: GdArray[String] = GdArray()
@gdexport var scores: GdArray[Int]  = GdArray()

tags.append("melee")
tags.append("magic")
println(s"size=${tags.size}  first='${tags(0)}'")

for tag <- tags do println(tag)
val list: List[String] = tags.toList
```

Supports: `size`, `isEmpty`, `apply(i)`, `update(i, v)`, `append`, `clear`,
`contains`, `indexOf`, `insert`, `removeAt`, `erase`, `sort`, `foreach`, `toList`.

Full `ToVariant`/`FromVariant`/`ExportType`/`DefaultValue` typeclasses.

**Lifetime:** `GdArray` does not currently manage the internal Godot Array refcount.
Call `.destroy()` on local temporaries to release the Godot handle. Treat exported
arrays as extension-lifetime handles.

### `GdDict[K, V]` — typed Godot Dictionary

```scala
val scores = GdDict[String, Int]()
scores("warrior") = damage
scores("mage")    = (damage * critChance).toInt
println(s"warrior=${scores("warrior")}")

for (k, v) <- scores do println(s"$k → $v")
val ks: GdArray[String] = scores.keys()
```

Supports: `size`, `isEmpty`, `has`, `get`, `apply`, `update`, `erase`, `clear`,
`keys`, `values`, `foreach`, `contains`.

**Lifetime:** same as `GdArray` — call `.destroy()` on locals, or let exported
dicts live for the extension lifetime.

### Packed Arrays

All 10 Godot `Packed*Array` types with element access:

`PackedByteArray`, `PackedInt32Array`, `PackedInt64Array`, `PackedFloat32Array`,
`PackedFloat64Array`, `PackedStringArray`, `PackedVector2Array`, `PackedVector3Array`,
`PackedVector4Array`, `PackedColorArray`

```scala
val arr: PackedFloat32Array = getFloatData()
val n = arr.size()
val first = arr(0)
val seq: Seq[Float] = arr.toSeq
```

### Export Wrappers

```scala
@gdexport var weapon:   Tres[Weapon]       = Tres.empty      // Resource picker
@gdexport var scene:    Tscn[EnemyScene]   = Tscn.empty      // Scene picker
@gdexport var required: Required[SomeNode] = Required.empty  // no clear button
```

- `Tres[T]` — Resource file picker; shows `ResourceType` hint in inspector
- `Tscn[T]` — PackedScene picker; filters to `.tscn` files
- `Required[A]` — disables the clear button; signals a required reference

---

## Memory Model

Two independent ownership systems run in parallel. Understanding both is required for
correct resource management.

```
Scala GC (Immix)                Godot refcount
────────────────────            ─────────────────────
GodotObject (Scala wrapper)     native engine object
  var ptr: Ptr[Byte] ─────────→   refcount ≥ 1
  (tracked by GC)                 (tracked by Godot)
```

**Scala GC:** manages Scala wrapper objects — `GodotObject` subclasses, closures,
collections. The Immix GC is **non-moving**: raw pointers held by Godot callbacks
into Scala memory (e.g. `CallbackRegistry` entries, instance maps) are guaranteed
to remain valid. You never `free` a Scala object.

**Godot refcount (Node subtree):** Godot owns nodes via the scene tree. Your Scala
wrapper holds a raw pointer. Call `free()` only for nodes you created and did
**not** add to the tree.

**Godot refcount (RefCounted subtree — Resource, AudioStream, Texture, etc.):**

```scala
// Acquire: Gd.newInstance[T] calls init_ref automatically
val res: Gd[Resource] = Gd.newInstance[Resource]

// Release: call unref() or use AutoCloseable
res.unref()

// Or with Using for scoped lifetime
Using(Gd.newInstance[Resource]) { res =>
  sprite.setTexture(res.get)
}
```

**Note:** generated engine methods that return `RefCounted` subtypes automatically call
`reference()` on the returned object (via `retSetup` in the generator). No manual
`.reference()` needed.

---

## Callables

Wrap Scala lambdas as Godot `Callable`:

```scala
val cb = CallableLambda { println("tween done") }
tween.tweenCallback(cb)

val cb2 = CallableLambda { println("timer fired") }
timer.connect("timeout", cb2)
```

Use `connect[A](signal)(f)` for typed signal connections when possible —
`CallableLambda` is for engine APIs that expect a raw `Callable` argument.

Supports 0–3 argument lambdas (0–3 arity). For 4+ args, connect directly via
`callbackRegistry` APIs (advanced).

---

## Hot-Reload

Extension classes support Godot's **hot-reload** without restarting the editor:

- Recompile → swap `.so` → Godot calls `recreate_instance_func` on existing nodes
- Live Scala instances are rebound to their existing engine objects (scene tree preserved)
- Node subclasses: `is_runtime = true` (reloads in editor without freezing)
- Resource/Object subclasses: `is_runtime = false`
- `unregisterAll(level)` filters by init level to avoid race conditions
- `@tool` classes are registered/unregistered at Editor level separately

---

## Editor Integration

### `EditorPlugin`

`@gdclass @tool class MyPlugin extends EditorPlugin` is auto-activated — the runtime
calls `editor_add_plugin` on registration and `editor_remove_plugin` on deinit
automatically.

```scala
@gdclass @tool class MyPlugin extends EditorPlugin:
    override def enterTree(): Unit =
        addToolMenuItem("My Action", CallableLambda { doSomething() })
        println("plugin activated")

    override def exitTree(): Unit =
        removeToolMenuItem("My Action")
```

### Custom class icon

```scala
@gdclass @tool @icon("res://icons/my_plugin.svg") class MyPlugin extends EditorPlugin:
    ...
```

### `EditorInspectorPlugin`

Extend the Godot inspector to add custom controls for your resource types:

```scala
@gdclass @tool class MyInspector extends EditorInspectorPlugin:
    override def canHandle(obj: GodotObject): Boolean =
        obj.isInstanceOf[MyResource]

    override def parseBegin(obj: GodotObject): Unit =
        addCustomControl(Label("My custom inspector UI"))

    override def parseProperty(
        obj: GodotObject, tpe: Int, name: String,
        hintType: Int, hintString: String, usageFlags: Int, wide: Boolean
    ): Boolean =
        false // return true to suppress the default inspector row for this property
```

Register it from the owning `EditorPlugin`:

```scala
@gdclass @tool class MyPlugin extends EditorPlugin:
    var inspector: Gd[MyInspector] = Gd.nullOf

    override def enterTree(): Unit =
        inspector = Gd.newInstance[MyInspector]
        addInspectorPlugin(inspector.get)

    override def exitTree(): Unit =
        removeInspectorPlugin(inspector.get)
        inspector.unref()
```

### `EditorNode3DGizmoPlugin`

Add custom 3D gizmos for your spatial node types:

```scala
@gdclass @tool class MyGizmoPlugin extends EditorNode3DGizmoPlugin:
    override def getGizmoName(): String = "MyGizmo"
    override def hasGizmo(node: Node3D): Boolean = node.isInstanceOf[MyNode3D]
    override def createGizmo(node: Node3D): EditorNode3DGizmo = ???

@gdclass @tool class MyPlugin extends EditorPlugin:
    var gizmoPlugin: Gd[MyGizmoPlugin] = Gd.nullOf
    override def enterTree(): Unit =
        gizmoPlugin = Gd.newInstance[MyGizmoPlugin]
        addNode3dGizmoPlugin(gizmoPlugin.get)
    override def exitTree(): Unit =
        removeNode3dGizmoPlugin(gizmoPlugin.get)
        gizmoPlugin.unref()
```

### `EditorImportPlugin`

Custom asset importers:

```scala
@gdclass @tool class MyImporter extends EditorImportPlugin:
    override def getImporterName(): String = "my.importer"
    override def getVisibleName(): String  = "My Format"
    override def getRecognizedExtensions(): PackedStringArray = ???
    override def getResourceType(): String = "Mesh"
    // "import" is a reserved Scala word, so this virtual keeps Godot's underscore-prefixed name
    // instead of colliding with it
    override def _import(
        sourceFile: String, savePath: String,
        options: GdDict[String, Ptr[Byte]],
        platformVariants: GdArray[String],
        genFiles: GdArray[String]
    ): Int = Error.OK.ordinal
```

### `EditorScript`

Run one-shot scripts from the editor (Script → Run):

```scala
@gdclass @tool class MyScript extends EditorScript:
    override def run(): Unit =
        println("running!")
        addRootNode(Node())
```

### Available editor plugin base classes

All of these can be extended with `@gdclass @tool`:

`EditorPlugin`, `EditorInspectorPlugin`, `EditorImportPlugin`, `EditorNode3DGizmoPlugin`,
`EditorExportPlugin`, `EditorDebuggerPlugin`, `EditorSyntaxHighlighter`,
`EditorTranslationParserPlugin`, `EditorContextMenuPlugin`, `EditorResourceConversionPlugin`,
`EditorVCSInterface`, `EditorScenePostImportPlugin`, `EditorScript`

---

## DX / Utilities

### `@onready`

Annotate a `lazy val` to express that it must be initialized after the node enters the
scene tree. The macro enforces `lazy val` at compile time; the field initializes on first
access (always safe in or after `ready`).

```scala
@gdclass class PlayerHud extends CanvasLayer:
  @onready lazy val healthBar: ProgressBar = getNode[ProgressBar]("HealthBar")
  @onready lazy val label: Label           = getNode[Label]("Label")

  override def ready(): Unit =
    healthBar.setMaxValue(100)
    label.setText("Ready!")
```

### `$"path"` — Node path shorthand

The `$` interpolator is generated as an extension on `StringContext` when a `Node`-derived
class is in scope:

```scala
@gdclass class HelloButtonSc extends CenterContainer:
  @onready lazy val btn = $"Button".as[Button]

  override def ready(): Unit = btn.setModulate(Color(1f, 0.5f, 0.5f, 1f))
```

Requires `import gdext.api.*` (which brings `GodotObject.as[T]` into scope).

### Output

```scala
import gdext.godot.Predef.*   // or via api import

print("Hello")               // Godot Output panel
println(s"speed=$speed")     // with newline
printerr("something failed") // red output
```

### Logging

```scala
Log.trace("fine detail")
Log.debug("debug info")
Log.info("general info")
Log.warn("potential problem")
Log.error("something broke")
```

All levels route to Godot's Output panel with a prefix tag.

### ResourceLoader

Typed convenience around `ResourceLoader.load`:

```scala
val tex: Gd[Texture2D] = ResourceLoader.singleton.load("res://icon.png")
```

**Planned:** `ResourceLoader.loadAs[T]` convenience wrapper.

### Null sentinel

```scala
var target: Player = nullOf    // instead of null
var boss: Enemy    = nullOf
```

---

## Code Generation

Codegen is wired directly into the build: `gdext.ffi`, `gdext.core`, and `gdext.api` each mix in a
generator trait (`FFIGeneratorModule`, `CoreGeneratorModule`, `APIGeneratorModule`) that overrides
Mill's `generatedSources` task, reading `gdextension_interface.json` / `extension_api.json` for the
module's target Godot version and emitting Scala sources on every compile — there's no separate
generate command to run, and nothing is checked into `src/` as generated output. Together they
produce the full engine surface:

- **Engine class wrappers** — one file per class (~1 023 total), real Scala inheritance
- **Builtin value types** — all 16 types with math operators and constructors
- **Utility functions** — `lerp`, `clamp`, `snapped`, etc. from `GlobalScope`
- **Virtual stubs** — `{Class}Virtuals` objects for override detection
- **Export types** — `ExportType` givens for all math/builtin types
- **Interface** — FFI handshake (`GDExtensionInterfaceGetProcAddress`)
- **Singleton access** — `Input.singleton`, `Engine.singleton`, etc.
- **StringName caching** — `StringNames.cached(name)` avoids per-call allocation
- **Lazy method binds** — `classdb_get_method_bind` called once on first use

---

## Build & Tooling

- **Mill** build system (not sbt); module layout: `gdext.ffi`, `gdext.core`, `gdext.api` — each
  cross-built once per supported Godot version (4.5.0, 4.6.1, 4.7.0) — plus the published
  `gdext.mill-plugin` artifact and the `gdext.generator-module-mill-plugin` that supplies the
  generator traits those cross modules mix in
- **Nix flake** — pinned Godot 4.5/4.6/4.7, Scala 3.8.3, Scala Native 0.5.11, LLVM, Mill
- **`just publishLocal`** — publishes `gdext` + `gdext-mill-plugin` to `~/.ivy2/local` for the
  target Godot version
- **`just run <example>`** — publishLocal, then build, link, and launch Godot for that example
- **Metals / IntelliJ** — full LSP support on framework and example sources
- **`Register.auto` scanner** — build-time scalameta source generator discovers all
  `@gdclass` types and emits `GeneratedRegistrations.scala` automatically.
  Also emits `GeneratedSignalHandles.scala`, `GeneratedGodotClasses.scala`,
  `GeneratedEntry.scala`.
- **Mill plugin** — `gdext-mill-plugin` is a real, publishable Mill plugin module; every
  `examples/*` directory is a standalone Mill build (own `build.mill` + `./mill` wrapper) that
  consumes it via `~/.ivy2/local`. Not yet published to a public repository.

---

## API Layers

The binding is designed around two layers:

**High-level (current):** macro-driven, annotation-based API for game developers.
`@gdclass`, `@gdexport`, `@func`, `@signal` handle all boilerplate.
Full C# scripting parity is the target.

**Low-level (planned):** direct FFI without macro overhead for library authors and
performance-critical code. Manual class registration, Variant construction/destruction,
`classdb_get_method_bind` / `object_method_bind_ptrcall` exposed directly.

---

## Known Limitations


- **`GdArray` / `GdDict` lifetime:** the internal Godot Array/Dictionary refcount is
  not managed; call `.destroy()` on local temporaries, or treat as extension-lifetime
  handles for exported properties.
- **`FromVariant` for `GdArray`/`GdDict`** heap-allocates 8 bytes per read (bounded leak).
- **Builtin value type conveniences:** named constants (`Vector2.ZERO`, `Vector3.UP`,
  etc.) and methods (`distanceTo`, `length`, `normalized`, `dot`, `cross`, `lerp`)
  are not yet generated. Only operators (`+`, `-`, `*`, `/`) and field accessors exist.
- **Signal arity limit:** `Signal0`–`Signal8` exist; beyond 8 is not yet supported.
- **`@async`/`@await` wiring:** annotations are defined but not yet implemented.
- **`@globalClass` wiring:** annotation is defined but not yet implemented.
- **`@rpc` wiring:** annotation is defined but not yet implemented.
- **`ResourceLoader.loadAs[T]`:** documented convenience wrapper is not yet implemented;
  use `ResourceLoader.singleton.load(...)` directly.
- **Registration ordering:** classes are registered in scan order; `class Child extends Parent`
  where both are user classes requires `Parent` to appear before `Child` in
  `GeneratedRegistrations.scala`.
- **ScalaDoc on engine classes:** descriptions from `extension_api.json` are not yet
  forwarded to generated wrappers.
- **Mill plugin not published:** `gdext-mill-plugin` exists and is consumable via
  `just publishLocal` / `~/.ivy2/local` (see the `examples/*` builds), but is not yet published
  to a public Maven repository.
- **`GdxApiV47` is a stub:** version-specific API loading for post-4.7 features is
  not yet implemented (icon registration works via hardcoded pointer in `GdxApi.initialize`).
- **Examples may not compile:** the example projects exercise the API but may have
  fallen out of sync with the active codebase. File issues if something doesn't build.

---

## Roadmap

### Phase 1 — Bug Fixes & Stability (immediate)

- [x] `StringName` type with full typeclasses
- [x] `NodePath` type with full typeclasses
- [x] `GdArray`/`GdDict` `destroy()` explicit destructor
- [x] `@icon` wired through to `classdb_register_extension_class_icon`
- [x] `ExportHint.flags` — bitfield export hint, `ExportHint.toolButton`
- [x] Typed `GdArray` export hint (`PROPERTY_HINT_ARRAY_TYPE`)
- [x] Fix examples to compile: signal emit pattern, `GdArray()` (was `GdArray.empty`),
      `Double` (was `Float`) for `_process`, `emitSignal` (was `emit`), `Vector2` math
- [x] Add `Vector2/3/4` extension methods (constants, `distanceTo`, `length`,
      `normalized`, `dot`, `cross`, `lerp`, `clamp`, `abs`, `sign`)
- [x] Auto-`reference()` for RefCounted engine returns (generator fix)
- [ ] `FromVariant` leak mitigation for `GdArray`/`GdDict`
- [ ] `ResourceLoader.loadAs[T]` convenience wrapper
- [x] `GdArray.empty[A]` companion method

### Phase 2 — Memory Model Overhaul (next)

- [ ] Deferred `unreference()` for RefCounted objects (adopt SwiftGodot pattern:
      queue via `Callable.callDeferred()`)
- [ ] Phantom-reference / `Cleaner`-based auto-`unref` for `Gd[T]`
- [ ] Proper `GdArray`/`GdDict` refcount management (internal `reference()`/`unreference()`)
- [ ] Fix `AutoCloseable` for non-RefCounted: `close()` calls `free()` universally

### Phase 3 — C# Parity Features (medium term)

- [ ] `@rpc` wiring — multiplayer RPC registration
- [ ] `@globalClass` wiring — global scope registration
- [ ] `@async` / `@await` wiring — scheduler implementation
- [ ] Property assignment sugar for value types (investigate macro desugar)
- [ ] Signal arity > 8 via `Tuple`-based generic signal
- [ ] `callDeferred` ergonomics: typed wrapper
- [ ] `ExportHint.placeholder` — placeholder text in inspector

### Phase 4 — Low-Level API & Plugin (long term)

- [ ] Zone ergonomics for low-level API
- [ ] Expose raw FFI: `GdxApi` as user-facing API, manual class registration
- [x] Mill plugin restructure — `gdext.ffi`/`gdext.core`/`gdext.api` cross-built per Godot
      version, `gdext-mill-plugin` artifact, examples consume via `~/.ivy2/local`
- [ ] Publish `gdext-mill-plugin` to a public Maven repository
- [ ] ScalaDoc forwarding from `extension_api.json`

### Phase 5 — Quality of Life (ongoing)

- [ ] Editor plugin infrastructure — verify all base classes
- [ ] Documentation accuracy sweep
- [ ] Regression test suite
- [ ] Benchmarks for hot-path dispatch
