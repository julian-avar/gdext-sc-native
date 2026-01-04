package godot.macros

import scala.annotation.StaticAnnotation

/** Marks a Scala class as a Godot class that should be registered with the engine's ClassDB.
  *
  * Example:
  * {{{
  * @GodotClass
  * class Player extends CharacterBody2D {
  *   // ...
  * }
  * }}}
  */
class GodotClass extends StaticAnnotation

/** Marks a property to be exposed to Godot's editor and serialization system.
  *
  * Example:
  * {{{
  * @GodotExport var speed: Float = 400.0f
  * }}}
  */
class GodotExport extends StaticAnnotation

/** Marks a method as callable from Godot scripts and other languages.
  *
  * Example:
  * {{{
  * @GodotMethod
  * def takeDamage(amount: Int): Unit = { ... }
  * }}}
  */
class GodotMethod extends StaticAnnotation

/** Defines a custom signal that can be emitted and connected to.
  *
  * Example:
  * {{{
  * @GodotSignal
  * val healthChanged: Signal1[Int] = Signal1()
  * }}}
  */
class GodotSignal extends StaticAnnotation
