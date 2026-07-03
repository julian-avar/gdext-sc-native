package com.`julian-avar`.gdext.core

import scala.quoted.*
import scala.scalanative.unsafe.*

trait GodotClass[T <: GodotObject]:
    def className: String
    def isRefCounted: Boolean
    def wrap(ptr: Ptr[Byte]): T
end GodotClass

object GodotClass:
    /** Derive a `GodotClass[T]` for a user-defined extension class at compile time.
      *
      * The derived instance uses the class's simple name as the Godot class name, assumes
      * `isRefCounted = false` (correct for Node subclasses), and wraps pointers by creating a fresh
      * instance and setting its `ptr` field.
      *
      * Place in the companion object of the user class so it's found by implicit resolution:
      * {{{
      * class Player extends CharacterBody2D:
      *   ...
      * object Player:
      *   given GodotClass[Player] = GodotClass.derived[Player]
      * }}}
      */
    inline def derived[T <: GodotObject]: GodotClass[T] = ${ derivedImpl[T] }

    private def derivedImpl[T <: GodotObject: Type](using Quotes): Expr[GodotClass[T]] =
        import quotes.reflect.*

        val tpe     = TypeRepr.of[T]
        val sym     = tpe.typeSymbol
        val name    = Expr(sym.name)
        val ctorSym = sym.primaryConstructor

        val requiredParams = ctorSym.paramSymss.flatten.filterNot(_.flags.is(Flags.HasDefault))
        if requiredParams.nonEmpty then
            report.errorAndAbort(
              s"GodotClass.derived[${sym
                      .name}]: primary constructor must have all-default params. " +
                  s"Required: ${requiredParams.map(_.name).mkString(", ")}"
            )
        end if

        val wrapFn: Expr[Ptr[Byte] => T] = '{ (p: Ptr[Byte]) =>
            // Return the canonical Scala instance if this godotPtr belongs to a user-defined class.
            // Falls back to creating a fresh wrapper for pure engine objects (not in the map).
            GdClassRegistry.lookupByPtr(p) match
                case Some(existing) => existing.asInstanceOf[T]
                case None           =>
                    val obj = ${ Apply(Select(New(Inferred(tpe)), ctorSym), Nil).asExprOf[T] }
                    obj.asInstanceOf[GodotObject].ptr = p
                    obj
        }

        '{
            new GodotClass[T]:
                def className             = $name
                def isRefCounted          = false
                def wrap(p: Ptr[Byte]): T = $wrapFn(p)
        }
    end derivedImpl

end GodotClass
