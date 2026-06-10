package gdext.generator

object Generator:
    case class ScalaFile(content: String, path: String, name: String)

    def types(types: Vector[Ast.Type]): Vector[ScalaFile] = types.groupBy(_.kind.name)
        .map { (name, ts) =>
            ScalaFile(
              content = TreesGenerator.TypesGen.typesSource(name, ts),
              path = "gdext/generated/types",
              name = name
            )
        }.toVector
    end types

    def interfaces(interfaces: Vector[Ast.Interface]): Vector[ScalaFile] = Vector(ScalaFile(
      content = TreesGenerator.InterfaceGen.interfaceSource(interfaces),
      path = "gdext/generated",
      name = "Interface"
    ))

    def generateBuiltins(builtins: Vector[Ast.BuiltinClass]): Vector[ScalaFile] =
        val sorted                  = topoSort(builtins)
        val (valueTypes, heapTypes) = sorted.partition(_.members.nonEmpty)
        Vector(ScalaFile(
          content = TreesGenerator.BuiltinsGen.builtinsSource(valueTypes, heapTypes),
          path = "gdext/generated",
          name = "GodotBuiltins"
        ))
    end generateBuiltins

    def classVirtuals(
        classes: Vector[Ast.GodotClass],
        valueBuiltins: Set[String] = Set.empty
    ): Vector[ScalaFile] =
        val byName = classes.map(c => c.name -> c).toMap

        def allVirtualsWithOwner(cls: Ast.GodotClass): Vector[(Ast.GodotMethod, String)] =
            val own            = cls.methods.filter(_.isVirtual).map(_ -> cls.name)
            val parentVirtuals = cls.inherits.flatMap(byName.get).map(allVirtualsWithOwner)
                .getOrElse(Vector.empty)
            val ownNames = own.map(_._1.name).toSet
            parentVirtuals.filterNot(v => ownNames.contains(v._1.name)) ++ own
        end allVirtualsWithOwner

        classes.flatMap { cls =>
            val virtuals = allVirtualsWithOwner(cls)
            if virtuals.isEmpty then None
            else
                val entries = TreesGenerator.VirtualsGen.virtualEntryTerms(virtuals, valueBuiltins)
                if entries.isEmpty then None
                else
                    Some(ScalaFile(
                      content = TreesGenerator.VirtualsGen.virtualsSource(cls, entries),
                      path = "gdext/generated/virtuals",
                      name = s"${cls.name}Virtuals"
                    ))
                end if
            end if
        }
    end classVirtuals

    def generateWrappers(
        classes: Vector[Ast.GodotClass],
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty
    ): Vector[ScalaFile] = classes.map { cls =>
        ScalaFile(
          content = TreesGenerator.WrapperGen.wrapperSource(cls, valueBuiltins, refcountedTypes),
          path = "gdext/generated/classes",
          name = cls.name
        )
    }

    def generateUtilityFunctions(
        utilities: Vector[Parser.UtilityFunction],
        valueBuiltins: Set[String] = Set.empty,
        refcountedTypes: Set[String] = Set.empty
    ): Vector[ScalaFile] = Vector(ScalaFile(
      content = TreesGenerator.UtilityGen.utilitySource(utilities, valueBuiltins, refcountedTypes),
      path = "gdext/generated",
      name = "UtilityFunctions"
    ))

    def generateGlobalScope(
        utilities: Vector[Parser.UtilityFunction],
        enums: Vector[Parser.GlobalEnum]
    ): Vector[ScalaFile] =
        Vector(ScalaFile(
          content = TreesGenerator.GlobalScopeGen.globalScopeSource(utilities, enums),
          path = "gdext/generated",
          name = "GlobalScope"
        ))

    def functionDefinition(comment: String, name: String, function: Ast.Kind.Function): String =
        TreesGenerator.functionDefinitionStr(comment, name, function)

    // ── Data helpers (no code generation) ────────────────────────────────────

    private def isPrimitiveMeta(meta: String): Boolean = TreesGenerator.isPrimitiveMeta(meta)

    private def topoSort(builtins: Vector[Ast.BuiltinClass]): Vector[Ast.BuiltinClass] =
        val byName                    = builtins.map(b => b.name -> b).toMap
        val visited                   = scala.collection.mutable.LinkedHashSet.empty[String]
        def visit(name: String): Unit = if !visited.contains(name) then
            byName.get(name).foreach { b =>
                b.members.filterNot(m => isPrimitiveMeta(m.meta)).foreach(m => visit(m.meta))
                visited += name
            }
        builtins.foreach(b => visit(b.name))
        visited.toVector.flatMap(byName.get)
    end topoSort
end Generator

package util {
    def formatComment(description: Option[String], deprecated: Option[Ast.Deprecated]) =
        if description.isEmpty && deprecated.isEmpty then ""
        else
            Vector(
              Vector("/**"),
              description.toVector.flatMap { desc => desc.split("\n").map { line => s" * $line" } },
              deprecated.toVector.flatMap { dep =>
                  Vector(
                    " *",
                    s" * @deprecated Since ${dep.since}. Use ${dep.replaceWith} instead."
                  )
              },
              Vector(" */")
            ).flatten.mkString("\n")

    def formatComment(`type`: Ast.Type): String =
        formatComment(`type`.description, `type`.deprecated)
}
