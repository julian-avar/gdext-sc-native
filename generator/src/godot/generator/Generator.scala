package godot.generator

import mainargs.{main, arg, ParserForMethods, Flag}

/** Code generator for Godot Scala Native bindings. Parses extension_api.json and generates Scala
  * code for all Godot classes.
  */
object Generator:
    @main
    def run(
        @arg(short = 'i', doc = "Path to extension_api.json")
        apiJson: String,
        @arg(short = 'o', doc = "Output directory for generated Scala files")
        outputDir: String,
        @arg(short = 'v', doc = "Verbose output")
        verbose: Flag = Flag(false)
    ): Unit =
        println(s"Godot Scala Native Bindings Generator")
        println(s"API JSON: $apiJson")
        println(s"Output:   $outputDir")
        println()

        // TODO: Implement the actual generation logic
        // 1. Parse extension_api.json using upickle
        // 2. Generate Scala classes for each Godot class
        // 3. Generate method bindings
        // 4. Generate property accessors
        // 5. Write files to output directory

        println("Generation not yet implemented")
        println("This will parse extension_api.json and generate:")
        println("  - Class hierarchies (Node, Node2D, Resource, etc.)")
        println("  - Method bindings with proper Scala types")
        println("  - Property accessors")
        println("  - Signal definitions")
        println("  - Virtual method hooks")
    end run

    def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
end Generator
