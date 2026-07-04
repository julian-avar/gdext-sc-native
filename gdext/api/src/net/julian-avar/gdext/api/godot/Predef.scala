package net.`julian-avar`.gdext.godot

import net.`julian-avar`.gdext
import gdext.core.{GodotObject, GdxApi}

// ── Output (GDScript-style, routes to Godot's Output panel) ──────────────────
// Godot's `print` always appends a newline; `println` is the Scala-idiomatic alias.
def print(msg: Any): Unit    = GdxApi.printString(msg.toString)
def println(msg: Any): Unit  = GdxApi.printString(msg.toString)
def printerr(msg: Any): Unit = GdxApi.printErrString(msg.toString)

// ── Null sentinel for GodotObject subclasses ──────────────────────────────────
// `var node: Player = nullOf` is cleaner than `null.asInstanceOf[Player]`.
// No GodotClass[T] given is required — this is just a typed null.
def nullOf[T <: GodotObject]: T = null.asInstanceOf[T]

// ── Structured / leveled logging ──────────────────────────────────────────────
export gdext.core.Log
