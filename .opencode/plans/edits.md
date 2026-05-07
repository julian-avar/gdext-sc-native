# Proposed Edits

## Generator.scala modifications

### Line 344: Change scalaType method
FROM:
```scala
case "String" | "StringName" => "CString"
```
TO:
```scala
case "String" | "StringName" => "String"
```

### Lines 354-389: Modify packArg method
Need to handle String/StringName types by:
1. Allocating space for a Godot String on the stack
2. Converting the Scala String to CString in a Zone
3. Initializing the Godot String using GdxApi.initGodotString
4. Passing the pointer to the Godot String buffer
5. Adding cleanup after ptrcall (calling GdxApi.destroyGodotString)

### Lines 392-416: Modify retSetup method
For String/StringName return types:
1. Allocate space for Godot String on stack
2. After ptrcall, convert Godot String to Scala String using appropriate GdxApi functions
3. Clean up the Godot String buffer