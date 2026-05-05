
package io.github.optical002.godot.codegen.gdextensioninterface.types

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.unsigned.UInt.*
import io.github.optical002.godot.types.*

/**
 * In this API there are multiple functions which expect the caller to pass a pointer
 * on return value as parameter.
 * In order to make it clear if the caller should initialize the return value or not
 * we have two flavor of types:
 * - `GDExtensionXXXPtr` for pointer on an initialized value
 * - `GDExtensionUninitializedXXXPtr` for pointer on uninitialized value
 * 
 * Notes:
 * - Not respecting those requirements can seems harmless, but will lead to unexpected
 * segfault or memory leak (for instance with a specific compiler/OS, or when two
 * native extensions start doing ptrcall on each other).
 * - Initialization must be done with the function pointer returned by `variant_get_ptr_constructor`,
 * zero-initializing the variable should not be considered a valid initialization method here !
 * - Some types have no destructor (see `extension_api.json`'s `has_destructor` field), for
 * them it is always safe to skip the constructor for the return value if you are in a hurry ;-)
 */
// type GDExtensionVariantPtr : Handle

// type GDExtensionConstVariantPtr : Handle

// type GDExtensionUninitializedVariantPtr : Handle

// type GDExtensionStringNamePtr : Handle

// type GDExtensionConstStringNamePtr : Handle

// type GDExtensionUninitializedStringNamePtr : Handle

// type GDExtensionStringPtr : Handle

// type GDExtensionConstStringPtr : Handle

// type GDExtensionUninitializedStringPtr : Handle

// type GDExtensionObjectPtr : Handle

// type GDExtensionConstObjectPtr : Handle

// type GDExtensionUninitializedObjectPtr : Handle

// type GDExtensionTypePtr : Handle

// type GDExtensionConstTypePtr : Handle

// type GDExtensionUninitializedTypePtr : Handle

// type GDExtensionMethodBindPtr : Handle

// type GDExtensionRefPtr : Handle

// type GDExtensionConstRefPtr : Handle

// type GDExtensionClassInstancePtr : Handle

// type GDExtensionClassLibraryPtr : Handle
/**
 * Pointer to custom ScriptInstance native implementation.
 */
// type GDExtensionScriptInstanceDataPtr : Handle

// type GDExtensionScriptLanguagePtr : Handle
/**
 * Pointer to ScriptInstance.
 */
// type GDExtensionScriptInstancePtr : Handle

