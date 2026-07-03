# Class Registration Lifecycle

User-defined Godot classes are registered via a multi-stage process that spans compile
time (macros, build-time scanner) and run time (init callbacks).

## Stage 1: Compile-Time — Macro Expansion

```mermaid
flowchart TB
    subgraph "Build-time scanner (scalameta)"
        SCAN["Scans all .scala files\nfor @gdclass annotations"]
        REGFILE["Generates GeneratedRegistrations.scala\nwith Register.auto[T]() for each class"]
        SIGFILE["Generates GeneratedSignalHandles.scala\nwith typed signal extension methods"]
        CLSFILE["Generates GeneratedGodotClasses.scala\nwith GodotClass given instances"]
        ENTRYFILE["Generates GeneratedEntry.scala\nwith @exported('godot_scala_init')"]
    end

    subgraph "Register.auto[T] macro (compile time)"
        READ["Reads: base class, @gdexport,\n@func, @signal, @tool, @icon,\n@export_group/category/subgroup"]
        VIRT["Detects overridden virtuals\nby comparing against\n{Parent}Virtuals.entries"]
        FACTORY["Generates factory closure:\n() => new T(arg1, arg2, ...)"]
        PROP["Generates property descriptors\nwith getter/setter trampolines"]
        METH["Generates method dispatch entries"]
        SIG["Generates signal descriptors"]
    end

    SCAN --> REGFILE & SIGFILE & CLSFILE & ENTRYFILE
```

## Stage 2: Init — C Entry Point

```mermaid
flowchart TB
    subgraph "Godot loads .so"
        GODOT[Godot calls\ngodot_scala_init]
    end

    subgraph "GeneratedEntry.godotScalaInit"
        REGALL["GeneratedRegistrations.registerAll()"]
        EACH["calls Register.auto[MyClass]()\nfor each @gdclass"]
        PUSH["each macro call emits\nGdClassRegistry.register(...)"]
        PUSHES["accumulates in\nGdClassRegistry.registrations\n(ListBuffer)"]
        INIT["gdext.GodotEntry.init\n(getProcAddress, library, initPtr)"]
    end

    subgraph "GodotEntry.init"
        GPA[stores getProcAddress]
        LIB[stores library pointer]
        API[GdxApi.initialize\nloads all function pointers]
        CALLBACK["registers init_callback\nand deinit_callback\nas CFuncPtrs"]
        FILL["fills GdxInitStruct:\ninitLevel = Editor\ninit_callback / deinit_callback"]
    end

    GODOT --> REGALL --> EACH --> PUSH --> PUSHES --> INIT
    INIT --> GPA & LIB & API & CALLBACK & FILL
```

## Stage 3: Godot Calls `init_callback(Scene)`

```mermaid
flowchart TB
    subgraph "init_callback (Scene level)"
        CR["ClassRegistrar.register(Scene)"]
    end

    subgraph "ClassRegistrar.register"
        LOOP["for each registration\nat this init level"]
        ALLOC["malloc StringName buffers\nfor className, parentName"]
        SN["stringNameNew(classNameBuf, name)"]
        UDATA["malloc(1) unique userdataPtr"]
        FACT["factoryMap += userdataPtr → factory"]
        FREE["freeFn = shared CFuncPtr\nthat evicts maps + free(instancePtr)"]
        VTBL["buildVirtualTable\ninterns virtual names\nstores dispatch IDs"]
        PTBL["buildPropertyInternTable\ninterns property names"]
        INFO["allocates ClassCreationInfo3\n160-byte struct"]
        CLASS["GdxApi.registerClass3\n(library, name, parent, info)"]
        ICON["registerIcon if @icon set"]
        PLUGIN["auto-activate EditorPlugin\nif parentName == 'EditorPlugin'"]
        PROP2["registerProperty / Group / Subgroup / Category"]
        METH2["buildMethodTable\nregisterMethod per @func"]
        SIG2["buildSignalTable\nregisterSignal per @signal"]
    end

    CR --> LOOP
    LOOP --> ALLOC --> SN --> UDATA --> FACT --> FREE
    LOOP --> VTBL
    LOOP --> PTBL
    LOOP --> INFO --> CLASS --> ICON --> PLUGIN
    LOOP --> PROP2
    LOOP --> METH2
    LOOP --> SIG2
```

## Instance Maps (Runtime State)

```mermaid
flowchart TB
    subgraph "ClassRegistrar singleton maps"
        IM["instanceMap: Ptr[Byte] to GodotObject (key=instancePtr, value=Scala instance)"]
        GPM["godotPtrMap: Ptr[Byte] to GodotObject (key=godot engine ptr, value=Scala instance)"]
        ITG["instanceToGodotPtr: Ptr[Byte] to Ptr[Byte] (key=instancePtr, value=godotPtr)"]
        ICM["instanceClassMap: Ptr[Byte] to Ptr[Byte] (key=instancePtr, value=userdataPtr)"]
        FM["factoryMap: Ptr[Byte] to factory (key=userdataPtr)"]
        FF["freeFns: String to FreeInstanceFn"]
        VT["virtualTables: userdataPtr to Array of (internedName, callDataBuf)"]
        PT["propertyTables: userdataPtr to Array of (internedName, PropertyDescriptor)"]
        DF["dispatchFns: Int to dispatch function"]
        MDF["methodDispatchFns: Int to method dispatch function"]
    end

    subgraph "Not thread-safe"
        NS["All maps are immutable Map vars reassigned via += (non-atomic RMW) or mutable.Map (no synchronization)"]
    end
```

## Free Callback

When Godot destroys an instance (or on hot-reload), it calls the registered
`free_instance_func`:

```mermaid
flowchart LR
    subgraph "freeFn (shared per class)"
        F1["instanceToGodotPtr.get(instancePtr)\n→ find godotPtr"]
        F2["godotPtrMap -= godotPtr"]
        F3["instanceToGodotPtr -= instancePtr"]
        F4["instanceMap -= instancePtr"]
        F5["instanceClassMap -= instancePtr"]
        F6["free(instancePtr)"]
    end

    F1 --> F2 --> F3 --> F4 --> F5 --> F6
```

Note: The Scala `GodotObject` subclass instance is **not** freed here — the Scala GC manages
it. Only the 1-byte `instancePtr` sentinel (malloc'd in `create_instance_func`) is freed.

## Files

- `gdext/core/src/gdext/core/ClassRegistrar.scala` — all maps, create/recreate/free, virtual/property/method dispatch
- `gdext/core/src/gdext/core/GdClassRegistry.scala` — `GdClassRegistration` case class, `ListBuffer`
- `gdext/core/src/gdext/core/Register.scala` — `Register.auto[T]` macro
- `gdext/core/src/gdext/core/GodotEntry.scala` — `init` function, init/deinit callbacks
- `build.mill` — `generatedSources` task (build-time scanner)
