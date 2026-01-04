package godot.internal

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

/** Low-level FFI bindings to Godot's GDExtension C API. These are direct mappings to the C
  * interface defined in gdextension_interface.h
  */
object GDExtension:

    // Opaque types from GDExtension
    type GDExtensionInterface       = Ptr[Byte]
    type GDExtensionClassLibraryPtr = Ptr[Byte]
    type GDExtensionInitialization  = Ptr[Byte]

    /** Entry point function signature for GDExtension. This is what Godot calls when loading the
      * extension.
      */
    type GDExtensionInitializationFunction = CFuncPtr3[
      GDExtensionInterface,
      GDExtensionClassLibraryPtr,
      GDExtensionInitialization,
      Boolean
    ]

    // TODO: Add more GDExtension FFI bindings as needed
    // - GDExtensionVariantType
    // - GDExtensionCallError
    // - Function pointers for class registration
    // - etc.
end GDExtension
