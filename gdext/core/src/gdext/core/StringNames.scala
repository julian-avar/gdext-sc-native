package gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.stdlib.*
import scala.scalanative.libc.string.*

/** Process-lifetime cache of Godot StringName handles keyed by their Scala string.
  *
  * Each handle is malloc'd once and never freed — the cache lives for the whole extension lifetime.
  * This mirrors how the reference binding caches StringNames: O(1) lookup on subsequent calls, no
  * allocation, no destructor needed.
  *
  * Must only be called after GdxApi.initialize() has run (i.e. from SCENE level init onwards, never
  * from Core/Servers init).
  */
object StringNames:
    private val cache = scala.collection.mutable.HashMap.empty[String, Ptr[Byte]]

    def cached(name: String): Ptr[Byte] = cache.getOrElseUpdate(
      name, {
          val buf = malloc(StringNameSize).asInstanceOf[Ptr[Byte]]
          memset(buf, 0, StringNameSize)
          Zone { GdxApi.initStringName(buf, toCString(name)) }
          buf
      }
    )
end StringNames
