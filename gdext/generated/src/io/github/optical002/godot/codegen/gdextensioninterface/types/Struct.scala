
package io.github.optical002.godot.codegen.gdextensioninterface.types

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.unsigned.UInt.*
import io.github.optical002.godot.types.*


// type GDExtensionCallError : Struct

// type GDExtensionInstanceBindingCallbacks : Struct

// type GDExtensionPropertyInfo : Struct

// type GDExtensionMethodInfo : Struct
/**
 *
 * @deprecated Since 4.2. Use GDExtensionClassCreationInfo4 instead.
 */
// type GDExtensionClassCreationInfo : Struct
/**
 *
 * @deprecated Since 4.3. Use GDExtensionClassCreationInfo4 instead.
 */
// type GDExtensionClassCreationInfo2 : Struct
/**
 *
 * @deprecated Since 4.4. Use GDExtensionClassCreationInfo4 instead.
 */
// type GDExtensionClassCreationInfo3 : Struct

// type GDExtensionClassCreationInfo4 : Struct

// type GDExtensionClassMethodInfo : Struct

// type GDExtensionClassVirtualMethodInfo : Struct
/**
 * Only `call_func` and `token` are strictly required, however, `object_id` should be passed if its not a static method.
 * 
 * `token` should point to an address that uniquely identifies the GDExtension (for example, the
 * `GDExtensionClassLibraryPtr` passed to the entry symbol function.
 * 
 * `hash_func`, `equal_func`, and `less_than_func` are optional. If not provided both `call_func` and
 * `callable_userdata` together are used as the identity of the callable for hashing and comparison purposes.
 * 
 * The hash returned by `hash_func` is cached, `hash_func` will not be called more than once per callable.
 * 
 * `is_valid_func` is necessary if the validity of the callable can change before destruction.
 * 
 * `free_func` is necessary if `callable_userdata` needs to be cleaned up when the callable is freed.
 *
 * @deprecated Since 4.3. Use GDExtensionCallableCustomInfo2 instead.
 */
// type GDExtensionCallableCustomInfo : Struct
/**
 * Only `call_func` and `token` are strictly required, however, `object_id` should be passed if its not a static method.
 * 
 * `token` should point to an address that uniquely identifies the GDExtension (for example, the
 * `GDExtensionClassLibraryPtr` passed to the entry symbol function.
 * 
 * `hash_func`, `equal_func`, and `less_than_func` are optional. If not provided both `call_func` and
 * `callable_userdata` together are used as the identity of the callable for hashing and comparison purposes.
 * 
 * The hash returned by `hash_func` is cached, `hash_func` will not be called more than once per callable.
 * 
 * `is_valid_func` is necessary if the validity of the callable can change before destruction.
 * 
 * `free_func` is necessary if `callable_userdata` needs to be cleaned up when the callable is freed.
 */
// type GDExtensionCallableCustomInfo2 : Struct
/**
 *
 * @deprecated Since 4.2. Use GDExtensionScriptInstanceInfo3 instead.
 */
// type GDExtensionScriptInstanceInfo : Struct
/**
 *
 * @deprecated Since 4.3. Use GDExtensionScriptInstanceInfo3 instead.
 */
// type GDExtensionScriptInstanceInfo2 : Struct

// type GDExtensionScriptInstanceInfo3 : Struct

// type GDExtensionInitialization : Struct

// type GDExtensionGodotVersion : Struct

// type GDExtensionGodotVersion2 : Struct

// type GDExtensionMainLoopCallbacks : Struct

