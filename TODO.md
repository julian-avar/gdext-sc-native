# Fix Godot Types Generation

## Current Status
- [x] Identified bug: Generator.types() TODO - only comments
- [x] Parsed Ast.scala implicitly via Parser

## Steps
- [ ] 1. Edit gdext/generator-module/src/gdext/generator/Generator.scala: Implement types() per Ast.Kind
  - struct: opaque CStructN + Tag + extension fields
  - enum: type Int + object vals  
  - handle: type Ptr[Byte]
  - alias: type alias
  - function: CFuncPtrN
- [ ] 2. mill gdext.`generator-module`.generate 
- [ ] 3. mill gdext.compile 
- [ ] 4. Fix any new errors (e.g. more missing types)
- [ ] 5. mill example.buildExtension
- [ ] 6. Test in Godot

Progress: 0/6
