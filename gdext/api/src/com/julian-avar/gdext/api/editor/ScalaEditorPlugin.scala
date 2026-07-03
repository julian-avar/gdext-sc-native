package com.`julian-avar`.gdext.editor

import com.`julian-avar`.gdext
import gdext.core.{gdclass, tool}
import gdext.generated.EditorPlugin

/** Base class for binding-provided editor plugins.
  *
  * Extend this class (or EditorPlugin directly) to add editor-level tooling. Annotate with
  * `@gdclass @tool` so the class is registered at `GdxInitLevel.Editor` and Godot runs it inside
  * the editor:
  *
  * {{{
  * @gdclass @tool
  * class MyPlugin extends ScalaEditorPlugin:
  *   override def _getPluginName(): String = "My Godot Scala Plugin"
  *   override def _enterTree(): Unit = Log.info("Plugin ready")
  *   override def _exitTree(): Unit  = Log.info("Plugin removed")
  * }}}
  *
  * Registration is automatic: `generatedSources` picks up the `@gdclass` annotation and emits
  * `Register.auto[MyPlugin]()`.
  *
  * The `isRuntime=false` flag (set automatically for all `@tool` classes by the macro) ensures
  * Godot runs `_enterTree` and `_exitTree` in the editor process.
  */
abstract class ScalaEditorPlugin extends EditorPlugin:
    override def _getPluginName(): String = "GodotScala"
