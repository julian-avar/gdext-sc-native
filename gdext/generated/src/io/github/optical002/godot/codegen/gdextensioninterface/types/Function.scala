
package io.github.optical002.godot.codegen.gdextensioninterface.types

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.unsigned.UInt.*
import io.github.optical002.godot.types.*


// type GDExtensionVariantFromTypeConstructorFunc : Function

// type GDExtensionTypeFromVariantConstructorFunc : Function

// type GDExtensionVariantGetInternalPtrFunc : Function

// type GDExtensionPtrOperatorEvaluator : Function

// type GDExtensionPtrBuiltInMethod : Function

// type GDExtensionPtrConstructor : Function

// type GDExtensionPtrDestructor : Function

// type GDExtensionPtrSetter : Function

// type GDExtensionPtrGetter : Function

// type GDExtensionPtrIndexedSetter : Function

// type GDExtensionPtrIndexedGetter : Function

// type GDExtensionPtrKeyedSetter : Function

// type GDExtensionPtrKeyedGetter : Function

// type GDExtensionPtrKeyedChecker : Function

// type GDExtensionPtrUtilityFunction : Function

// type GDExtensionClassConstructor : Function

// type GDExtensionInstanceBindingCreateCallback : Function

// type GDExtensionInstanceBindingFreeCallback : Function

// type GDExtensionInstanceBindingReferenceCallback : Function

// type GDExtensionClassSet : Function

// type GDExtensionClassGet : Function

// type GDExtensionClassGetRID : Function

// type GDExtensionClassGetPropertyList : Function

// type GDExtensionClassFreePropertyList : Function

// type GDExtensionClassFreePropertyList2 : Function

// type GDExtensionClassPropertyCanRevert : Function

// type GDExtensionClassPropertyGetRevert : Function

// type GDExtensionClassValidateProperty : Function
/**
 *
 * @deprecated Since 4.2. Use GDExtensionClassNotification2 instead.
 */
// type GDExtensionClassNotification : Function

// type GDExtensionClassNotification2 : Function

// type GDExtensionClassToString : Function

// type GDExtensionClassReference : Function

// type GDExtensionClassUnreference : Function

// type GDExtensionClassCallVirtual : Function

// type GDExtensionClassCreateInstance : Function

// type GDExtensionClassCreateInstance2 : Function

// type GDExtensionClassFreeInstance : Function

// type GDExtensionClassRecreateInstance : Function

// type GDExtensionClassGetVirtual : Function

// type GDExtensionClassGetVirtual2 : Function

// type GDExtensionClassGetVirtualCallData : Function

// type GDExtensionClassGetVirtualCallData2 : Function

// type GDExtensionClassCallVirtualWithData : Function
/**
 * Passed a pointer to a PackedStringArray that should be filled with the classes that may be used by the GDExtension.
 */
// type GDExtensionEditorGetClassesUsedCallback : Function

// type GDExtensionClassMethodCall : Function

// type GDExtensionClassMethodValidatedCall : Function

// type GDExtensionClassMethodPtrCall : Function

// type GDExtensionCallableCustomCall : Function

// type GDExtensionCallableCustomIsValid : Function

// type GDExtensionCallableCustomFree : Function

// type GDExtensionCallableCustomHash : Function

// type GDExtensionCallableCustomEqual : Function

// type GDExtensionCallableCustomLessThan : Function

// type GDExtensionCallableCustomToString : Function

// type GDExtensionCallableCustomGetArgumentCount : Function

// type GDExtensionScriptInstanceSet : Function

// type GDExtensionScriptInstanceGet : Function

// type GDExtensionScriptInstanceGetPropertyList : Function
/**
 *
 * @deprecated Since 4.3. Use GDExtensionScriptInstanceFreePropertyList2 instead.
 */
// type GDExtensionScriptInstanceFreePropertyList : Function

// type GDExtensionScriptInstanceFreePropertyList2 : Function

// type GDExtensionScriptInstanceGetClassCategory : Function

// type GDExtensionScriptInstanceGetPropertyType : Function

// type GDExtensionScriptInstanceValidateProperty : Function

// type GDExtensionScriptInstancePropertyCanRevert : Function

// type GDExtensionScriptInstancePropertyGetRevert : Function

// type GDExtensionScriptInstanceGetOwner : Function

// type GDExtensionScriptInstancePropertyStateAdd : Function

// type GDExtensionScriptInstanceGetPropertyState : Function

// type GDExtensionScriptInstanceGetMethodList : Function
/**
 *
 * @deprecated Since 4.3. Use GDExtensionScriptInstanceFreeMethodList2 instead.
 */
// type GDExtensionScriptInstanceFreeMethodList : Function

// type GDExtensionScriptInstanceFreeMethodList2 : Function

// type GDExtensionScriptInstanceHasMethod : Function

// type GDExtensionScriptInstanceGetMethodArgumentCount : Function

// type GDExtensionScriptInstanceCall : Function
/**
 *
 * @deprecated Since 4.2. Use GDExtensionScriptInstanceNotification2 instead.
 */
// type GDExtensionScriptInstanceNotification : Function

// type GDExtensionScriptInstanceNotification2 : Function

// type GDExtensionScriptInstanceToString : Function

// type GDExtensionScriptInstanceRefCountIncremented : Function

// type GDExtensionScriptInstanceRefCountDecremented : Function

// type GDExtensionScriptInstanceGetScript : Function

// type GDExtensionScriptInstanceIsPlaceholder : Function

// type GDExtensionScriptInstanceGetLanguage : Function

// type GDExtensionScriptInstanceFree : Function

// type GDExtensionWorkerThreadPoolGroupTask : Function

// type GDExtensionWorkerThreadPoolTask : Function

// type GDExtensionInitializeCallback : Function

// type GDExtensionDeinitializeCallback : Function

// type GDExtensionInterfaceFunctionPtr : Function

// type GDExtensionInterfaceGetProcAddress : Function
/**
 * Each GDExtension should define a C function that matches the signature of GDExtensionInitializationFunction,
 * and export it so that it can be loaded via dlopen() or equivalent for the given platform.
 * 
 * For example:
 * 
 *   GDExtensionBool my_extension_init(GDExtensionInterfaceGetProcAddress p_get_proc_address, GDExtensionClassLibraryPtr p_library, GDExtensionInitialization *r_initialization);
 * 
 * This function's name must be specified as the 'entry_symbol' in the .gdextension file.
 * 
 * This makes it the entry point of the GDExtension and will be called on initialization.
 * 
 * The GDExtension can then modify the r_initialization structure, setting the minimum initialization level,
 * and providing pointers to functions that will be called at various stages of initialization/shutdown.
 * 
 * The rest of the GDExtension's interface to Godot consists of function pointers that can be loaded
 * by calling p_get_proc_address("...") with the name of the function.
 * 
 * For example:
 * 
 *   GDExtensionInterfaceGetGodotVersion get_godot_version = (GDExtensionInterfaceGetGodotVersion)p_get_proc_address("get_godot_version");
 * 
 * (Note that snippet may cause "cast between incompatible function types" on some compilers, you can
 * silence this by adding an intermediary `void*` cast.)
 * 
 * You can then call it like a normal function:
 * 
 *   GDExtensionGodotVersion godot_version;
 *   get_godot_version(&godot_version);
 *   printf("Godot v%d.%d.%d\n", godot_version.major, godot_version.minor, godot_version.patch);
 * 
 * All of these interface functions are described below, together with the name that's used to load it,
 * and the function pointer typedef that shows its signature.
 */
// type GDExtensionInitializationFunction : Function
/**
 * Called when starting the main loop.
 */
// type GDExtensionMainLoopStartupCallback : Function
/**
 * Called when shutting down the main loop.
 */
// type GDExtensionMainLoopShutdownCallback : Function
/**
 * Called for every frame iteration of the main loop.
 */
// type GDExtensionMainLoopFrameCallback : Function

