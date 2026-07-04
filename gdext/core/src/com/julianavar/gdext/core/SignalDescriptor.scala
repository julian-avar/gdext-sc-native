package com.julianavar.gdext.core

/** Type info for one parameter of a typed signal.
  *
  * Produced by the `Register.auto[T]` macro from the constructor params of `@signal case class`
  * declarations. Passed to `classdb_register_extension_class_signal` so Godot knows the signal's
  * full signature (shown in the editor, enforced on connection).
  *
  * `className` is only relevant for Object-derived types (variant type = 24). For all others it
  * should be the empty string.
  */
case class SignalParamInfo(name: String, variantType: Int, className: String = "")

/** Describes a signal declared on a user-defined extension class.
  *
  * Produced by the `Register.auto[T]` macro from `@signal case class` declarations. Passed to
  * `GdClassRegistry.register` and forwarded to `ClassRegistrar` for FFI registration.
  *
  * Example:
  * {{{
  * @signal case class died()
  * @signal case class hit(damage: Int, from: String)
  * }}}
  */
case class SignalDescriptor(name: String, params: List[SignalParamInfo] = Nil)
