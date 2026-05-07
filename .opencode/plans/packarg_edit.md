# PackArg Method Edit for Generator.scala

## Current code (lines 382-384):
```scala
case "String" | "StringName" | "Variant" | "void*" | "Array" => ("", param)
```

## Proposed change:
```scala
case "String" | "StringName" => (
  s"""val $slot = stackalloc[Byte](8) // Godot String is 8 bytes
     |  Zone {
     |    val cstr = toCString($param)
     |    GdxApi.initGodotString($slot, cstr)
     |  }""".stripMargin,
  s"$slot.asInstanceOf[Ptr[Byte]]"
)
case "Variant" | "void*" | "Array" => ("", param)
```
