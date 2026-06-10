# TODO

Godot 4 GDExtension framework for Scala Native. The hardest parts — FFI handshake, class registration, virtual dispatch, ptrcall, and a full code generator --- are done. What remains is ergonomics and the Variant layer.

---

## Extension Classes

- [x] Extend any Godot built-in type
  ```scala
  @gdclass
  class SpinningCube extends Node3D:
    override def _ready(): Unit = GdxApi.printString("Hello from Scala!")
  ```
- [x] Class appears in Godot's **ClassDB** and scene tree
- [x] `@gdclass` annotation (currently a marker; registration is still explicit in the entry point)
- [ ] `@gdclass` macro — auto-generate the `GdClassRegistry.register(...)` call; currently hand-written:
  ```scala
  // what you write today
  GdClassRegistry.register("SpinningCube", "Node3D", () => new SpinningCube(), Node3DVirtuals.entries)
  GodotEntry.init(getProcAddress, library, initPtr, () => ...)
  ```
- [x] `@tool` annotation stub
- [ ] `@tool` — run extension class logic in the editor

---

## Virtual Methods

- [x] Override any Godot virtual method (`_ready`, `_process`, `_physicsProcess`, `_input`, etc.)
  ```scala
  @gdclass
  class PlayerSc extends CharacterBody2D:
    override def _physicsProcess(delta: Double): Unit =
      moveAndSlide()
  ```
- [x] Generated `{Class}Virtuals` objects covering the full Godot class hierarchy

---

## Node References

- [x] `$"NodePath"` string-interpolation shortcut
  ```scala
  val btn = $"Button".as(new Button(_))
  ```
- [x] `.as(new T(_))` cast from `Node` to a concrete generated wrapper type
- [x] `@onready` annotation stub
- [ ] `@onready` macro — convert an annotated `lazy val` into an auto-injected fetch at `_ready` time:
  ```scala
  @onready
  lazy val btn = $"HBoxContainer/Button".as(new Button(_))
  // goal: the $"…".as(…) call is injected automatically at _ready; the val is just a declaration
  ```

---

## Editor Properties

- [x] Export a property to the **Inspector** (visible and editable in the Godot editor)
- [x] `@export` annotation stub
- [x] Variant marshaling for `Int`, `Float`, `Bool` — sufficient to export the most common field types
- [x] Property registration wired to `classdb_register_extension_class_property`
- [ ] `@export` macro — auto-generate the `PropertyDescriptor` boilerplate from the annotation:
  ```scala
  @export
  var speed = 400    // goal: no PropertyDescriptor needed
  
  // what you write today
  PropertyDescriptor(
    name = "speed",
    variantType = VariantType.Int,
    getter = (obj, ret) => Variant.writeInt(ret, obj.asInstanceOf[PlayerSc].speed.toLong),
    setter = (obj, v)   => obj.asInstanceOf[PlayerSc].speed = Variant.readInt(v).toInt
  )
  ```
- [ ] `@export_range(min, max[, step])` hint
- [ ] `@export_flags(...)` hint
- [ ] Exported `String` properties
- [ ] Exported `Object` / `Gd[T]` properties
- [ ] Exported `Array` / `Dictionary` / `PackedXxxArray` properties

---

## Signals

- [x] Connect a Godot signal to a Scala callback (no-argument signals)
  ```scala
  btn.connect("pressed")(() => GdxApi.printString("clicked!"))
  ```
- [ ] Typed signal arguments forwarded to the callback
  ```scala
  healthBar.connect("value_changed")((newValue: Double) => updateHud(newValue))
  ```
- [ ] `@signal` — declare a user-defined signal on an extension class, registered with Godot at class-registration time
  ```scala
  @signal
  def healthChanged(amount: Int): Unit = ()
  ```
- [ ] `emit_signal` from Scala
- [ ] `disconnect` — remove a signal connection

---

## GDScript Interop

- [x] Register a Scala method so GDScript can call it via `MethodEntry`
  ```scala
  MethodEntry(
    "_on_button_pressed",
    (obj, _, _) => obj.asInstanceOf[MyNode]._onButtonPressed()
  )
  ```
- [x] `@func` annotation stub
- [ ] `@func` macro — auto-generate `MethodEntry` from the annotation:
  ```scala
  @func
  def onButtonPressed(): Unit = ...    // goal: no MethodEntry boilerplate
  ```
- [x] Attach a `.scala` file as a script in the Godot editor (ScalaScriptLanguage integration)

---

## Type System

- [x] `Gd[T]` — zero-cost typed reference to a Godot engine object
  ```scala
  val enemy: Gd[CharacterBody2D] = findChild("Enemy").get
  enemy.setVelocity(Vector2(0f, 0f))
  ```
- [x] Covariant: `Gd[Button]` is a `Gd[Node]`
- [x] `.isNull` / `.toOpt` / `.widen[U]` / `.rawPtr`
- [x] Builtin value types (`Vector2`, `Vector3`, `Color`, `Rect2`, `Transform2D`, …) as zero-overhead opaque structs
  ```scala
  val dir = Vector2(1f, 0f)
  val scaled = dir * 400f       // inline math operators
  ```
- [ ] Variant marshaling for `String`
- [ ] Variant marshaling for `Object` / `Gd[T]`
- [ ] Variant marshaling for `Array`, `Dictionary`, `PackedXxxArray`
- [ ] `RefCounted` safety — auto `reference()` / `unreference()` on `Gd[T]` for `RefCounted` subtypes (currently, `RefCounted` objects leak if not freed manually)

---

## Generated Bindings

The code generator reads Godot's `extension_api.json` and emits Scala sources for all engine classes,
builtins, types, and utility functions.

- [x] All engine class wrappers (`gdext/generated/classes/`)
- [x] All builtin value types (`GodotBuiltins.scala`)
- [x] GDExtension interface bindings (`Interface.scala`)
- [x] Utility functions (`UtilityFunctions.scala`)
- [x] Lazy `Binds` — no explicit `loadBinds()` call needed; binds load on first use
- [x] Property getters/setters as Scala `def` / `def_=` pairs where available
- [x] Singleton access via `ClassName.singleton`
  ```scala
  Input.singleton.isActionPressed(c"jump")
  Engine.singleton.getFramesPerSecond()
  ```
- [ ] ScalaDoc on generated class/method/property bindings (descriptions from Godot's API JSON)
  - [x] Type aliases and enums
  - [x] GDExtension interface functions
  - [ ] Engine class wrappers (the main `gdext/generated/classes/` files)
  - [ ] Builtin types
  - [ ] Utility functions
  - [ ] `@param` tags on method parameters
  - [ ] Full `@deprecated` messages (field is parsed; not yet emitted)

---

## Tooling

- [x] Nix flake with pinned Godot, Scala Native, and Mill
- [x] `just run` — generate bindings, compile, and launch Godot in one command
- [x] Code generator (`mill gdext.generator.generate`)
- [x] Metals / IntelliJ support on the framework and example sources
- [ ] Published Mill plugin — users declare a dependency instead of cloning the repo
- [ ] sbt plugin
- [ ] Hot-reload / faster iteration (recompile `.so` without restarting Godot)
