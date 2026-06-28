# Features

Godot 4.x GDExtension binding for Scala Native. Targets C# parity for the scripting surface.
API is unstable — breaking changes expected.

---

## Extension Classes

Define extension classes with a single annotation. The `Register.auto` macro scans the class
at compile time and emits all registration boilerplate.

```scala
@gdclass class SpinningCube extends Node3D:
    override def _ready(): Unit = println("Hello from Scala!")
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
    override def _ready(): Unit = println("ready")
    override def _process(delta: Double): Unit = ()
    override def _physicsProcess(delta: Double): Unit = moveAndSlide()
    override def _input(event: InputEvent): Unit = ()
    override def _unhandledInput(event: InputEvent): Unit = ()
    override def _enterTree(): Unit = ()
    override def _exitTree(): Unit = ()
```

All lifecycle virtuals from the generated `{Class}Virtuals` stubs are detected automatically.
No explicit virtual registration call needed.

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
@gdexport(ExportHint.range(0, 100, 1))       var health: Int = 100
@gdexport(ExportHint.range(0.0, 1.0, 0.01)) var opacity: Double = 1.0
@gdexport(ExportHint.multiline)              var bio: String = ""
@gdexport(ExportHint.colorNoAlpha)           var tint: Color = Color(1f,1f,1f,1f)
@gdexport(ExportHint.file("*.png"))          var icon: String = ""
@gdexport(ExportHint.dir)                    var saveDir: String = ""
@gdexport(ExportHint.resourceType("Texture2D")) var tex: Tres[Texture2D] = Tres.empty
@gdexport(ExportHint.nodeType("CharacterBody2D")) var target: Gd[CharacterBody2D] = nullOf
@gdexport(ExportHint.enum("A", "B", "C"))    var mode: Int = 0
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
  @func def getOffset(): Vector2 = getPosition()          // math type return
  @func def scaleDamage(factor: Double): Double = damage * factor
  @func def onBodyEntered(body: PhysicsBody2D): Unit = () // engine object param
```

- `@func` — registers the method in ClassDB; callable from GDScript and via `call()`
- camelCase Scala names are exposed as `snake_case` Godot names automatically
- Supports `Int`, `Long`, `Float`, `Double`, `Bool`, `String`, `Vector2/3/4`, `Color`,
  `Rect2`, `Transform2D/3D`, `AABB`, `Quaternion`, `Plane`, `Projection`,
  `Gd[T]`, direct `T <: GodotObject`, `RID`

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

```scala
// Via auto-generated typed handle (preferred)
this.moved.emitSignal(velocity.x, velocity.y)
this.died.emitSignal()
this.healthChanged.emitSignal(oldHp, newHp)
```

Signal handles are generated as extension methods on the class.
Convention: capitalize the case class (`Moved`), lowercase the handle (`moved`).

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

### `Signal0/1/2/3` handles

```scala
val sig: Signal2[Float, Float] = player.moved   // auto-generated
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
- `instanceId` — stable Godot object ID
- `cast[U]` — typed downcast via `object_cast_to`
- `nullOf[T]` — null sentinel for nullable `T <: GodotObject` vars

```scala
var target: Player = nullOf   // preferred over null
```

### Math / Builtin Value Types

All 16 Godot builtin value types are available as Scala Native structs with full
operator support generated from the Godot API:

`Vector2`, `Vector3`, `Vector4`, `Vector2i`, `Vector3i`, `Vector4i`,
`Color`, `Rect2`, `Rect2i`, `Transform2D`, `Transform3D`, `AABB`,
`Quaternion`, `Plane`, `Projection`, `Basis`

```scala
val dir = Vector2(1f, 0f)
val scaled = dir * speed.toFloat
val clamped = speed.clamp(0f, 800f)
```

**Zone convention**: Methods that *return* a value builtin require a `Zone` in scope
(memory is stack-allocated in the zone, not heap-leaked):

```scala
Zone {
  val dir = Input.getVector("left", "right", "up", "down")
  velocity = dir * speed.toFloat
}
```

Property setters for value builtins work normally. Use explicit setter methods
(`setVelocity(v)`, `setPosition(v)`) — assignment sugar (`obj.velocity = v`) is
not available for value-type properties due to the Zone requirement.

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

## Callables

Wrap Scala lambdas as Godot `Callable`:

```scala
val cb = CallableLambda { println("tween done") }
tween.tweenCallback(cb)

val cb2 = CallableLambda { println("timer fired") }
timer.connect("timeout", cb2)
```

Use `connect[A](signal)(f)` for typed signal connections instead when possible —
`CallableLambda` is for engine APIs that expect a raw `Callable` argument.

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

```scala
@gdclass @tool class MyPlugin extends EditorPlugin:
  override def _enterTree(): Unit = println("plugin activated")
  override def _exitTree(): Unit  = println("plugin deactivated")
```

- `@tool` combined with `EditorPlugin` subclass — auto-calls `editor_add_plugin`
  on registration and `editor_remove_plugin` on deinit
- Register inspector plugins, custom gizmos, or editor UI via the full `EditorPlugin` API

---

## DX / Utilities

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

```scala
val tex: Gd[Texture2D] = ResourceLoader.loadAs[Texture2D]("res://icon.png")
```

Typed convenience wrapper around `ResourceLoader.load`.

### Null sentinel

```scala
var target: Player = nullOf    // instead of null
var boss: Enemy    = nullOf
```

---

## Code Generation

The generator (`mill gdext.generator.generate`) reads Godot's `extension_api.json`
and emits Scala sources for the full engine surface:

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

- **Mill** build system (not sbt); module layout: `gdext.core`, `gdext.generated`, `gdext.generator`
- **Nix flake** — pinned Godot 4.5/4.6, Scala 3.8.3, Scala Native 0.5.11, LLVM, Mill
- **`just run <example>`** — generate, compile, link, launch Godot in one command
- **Metals / IntelliJ** — full LSP support on framework and example sources
- **`Register.auto` scanner** — build-time scalameta source generator discovers all
  `@gdclass` types and emits `GeneratedRegistrations.scala` automatically

---

## Known Limitations

- **Property assignment sugar for value builtins is unavailable**: use `setVelocity(v)` not `velocity = v` — Zone memory makes the setter-as-assignment pattern unsound.
- **GdArray / GdDict lifetime**: the internal Godot Array/Dictionary refcount is not managed; treat them as extension-lifetime handles or call `destroy()` explicitly for temporaries.
- **`GodotClass[T]` given is manual**: each `@gdclass` file must include `given GodotClass[T] = GodotClass.derived[T]` (auto-generation planned).
- **Registration ordering**: classes are currently registered in scan order; `class Child extends Parent` where both are user classes requires `Parent` to appear before `Child` in `GeneratedRegistrations.scala`.
- **ScalaDoc on engine classes**: descriptions from `extension_api.json` are not yet forwarded to generated wrappers.
- **No published plugin**: users clone the repo; a published Mill plugin is planned.
