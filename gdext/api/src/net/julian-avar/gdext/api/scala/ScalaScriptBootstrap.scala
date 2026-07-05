package net.`julian-avar`.gdext.scala

import net.`julian-avar`.gdext
import gdext.core.*
import gdext.generated.*

/** Wires up Scala scripting support (the "Attach Script" editor workflow) for every project built
  * with this Mill plugin. Invoked automatically from generated entry code (`GeneratedEntry.scala`)
  * — see `GodotScalaNativeModule`'s `generatedSources` template. Not meant to be called by user
  * code directly.
  */
object ScalaScriptBootstrap:
    /** Enqueue ClassDB registrations for the Scala scripting classes. Must run before
      * `ClassRegistrar.register` snapshots `GdClassRegistry` (i.e. before `GodotEntry.init`).
      */
    def registerClasses(): Unit =
        GdClassRegistry.register(
          "ScalaScriptLanguage",
          "ScriptLanguageExtension",
          () => new ScalaScriptLanguage(),
          ScalaScriptLanguage.entries,
          isRuntime = false
        )
        GdClassRegistry.register(
          "ScalaScript",
          "ScriptExtension",
          () => new ScalaScript(),
          ScalaScript.entries,
          isRuntime = false
        )
        GdClassRegistry.register(
          "ScalaScriptResourceFormatLoader",
          "ResourceFormatLoader",
          () => new ScalaScriptResourceFormatLoader(),
          ScalaScriptResourceFormatLoader.entries,
          isRuntime = false
        )
        GdClassRegistry.register(
          "ScalaScriptResourceFormatSaver",
          "ResourceFormatSaver",
          () => new ScalaScriptResourceFormatSaver(),
          ScalaScriptResourceFormatSaver.entries,
          isRuntime = false
        )
    end registerClasses

    /** Construct one instance of each registered class and activate it with the engine. Must run
      * after ClassDB registration has completed (i.e. from `GodotEntry.init`'s `onSceneInit`
      * hook) — runs in every process that loads the extension, including a spawned "Play" child
      * process, which is what makes an attached script execute at actual runtime rather than only
      * appearing correctly in the editor UI.
      */
    def activateLanguage(): Unit =
        val langPtr = GdxApi.constructObject(StringNames.cached("ScalaScriptLanguage"))
        GdClassRegistry.lookupByPtr(langPtr).foreach { instance =>
            ScalaScriptLanguageSupport.langInstance = instance.asInstanceOf[ScalaScriptLanguage]
        }
        if ScalaScriptLanguageSupport.langInstance != null then
            Engine.registerScriptLanguage(ScalaScriptLanguageSupport.langInstance)

        val loaderPtr = GdxApi.constructObject(StringNames.cached("ScalaScriptResourceFormatLoader"))
        GdClassRegistry.lookupByPtr(loaderPtr).foreach {
            case loader: ScalaScriptResourceFormatLoader =>
                loader.reference()
                ResourceLoader.addResourceFormatLoader(loader)
            case _ => ()
        }

        val saverPtr = GdxApi.constructObject(StringNames.cached("ScalaScriptResourceFormatSaver"))
        GdClassRegistry.lookupByPtr(saverPtr).foreach {
            case saver: ScalaScriptResourceFormatSaver =>
                saver.reference()
                ResourceSaver.addResourceFormatSaver(saver)
            case _ => ()
        }
    end activateLanguage
end ScalaScriptBootstrap
