package com.pepegar

import scala.reflect.macros.blackbox
import scala.language.experimental.macros
import scala.annotation.StaticAnnotation
import scala.annotation.compileTimeOnly

object fixie {

  @compileTimeOnly("enable macro paradise to expand macro annotations")
  class fixie extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro fixieMacro.impl
  }

  object fixieMacro {
    def impl(c: blackbox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
      import c.universe._
      import Flag._
      val inputs = annottees.map(_.tree).toList

      require(inputs.length == 2, "@fixie should annotate a sealed [trait|abstract class] with companion")

      val clait: ClassDef      = inputs.collect({ case c: ClassDef  => c }).head
      val companion: ModuleDef = inputs.collect({ case c: ModuleDef => c }).head

      val A: TypeName                   = c.freshName(TypeName("A"))
      val NonRecursiveAdtName: TypeName = TypeName(s"${clait.name}F")
      val NonRecursiveAdtFullName: AppliedTypeTree =
        AppliedTypeTree(Ident(NonRecursiveAdtName), (clait.tparams.map(_.name) :+ A).map(x => Ident(x)))

      def isSealed(classOrTrait: ClassDef): Boolean =
        classOrTrait.mods.hasFlag(TRAIT) ||
          classOrTrait.mods.hasFlag(ABSTRACT) &&
            classOrTrait.mods.hasFlag(SEALED)

      val isCase: PartialFunction[Tree, Tree] = {
        case c: ClassDef if c.mods.hasFlag(CASE)  => c
        case c: ModuleDef if c.mods.hasFlag(CASE) => c
      }

      // Things to do:
      // 1. [X] create the parametrized Adt declaration
      // 2. [X] create all the cases for it
      // 3. [ ] create the functor instance
      // 4. [ ] create conversions between recursive & nonrecursive ADT

      val AdtCases = companion.impl.body.collect(isCase)

      val convertCaseClassParam: ValDef => ValDef = { valDef =>
        val recursive = valDef.tpt.toString == clait.name.toString || valDef.tpt.toString.contains(clait.name.toString)

        if (recursive) {
          ValDef(valDef.mods, valDef.name, Ident(A), valDef.rhs)
        } else valDef
      }

      def convertCaseObject(module: ModuleDef): ClassDef = {
        val Name: TypeName = TypeName(s"${module.name}F")

        q"case class $Name[..${clait.tparams}, $A]() extends $NonRecursiveAdtFullName"
      }

      def convertCaseClass(caseClass: ClassDef): ClassDef = {
        val Name: TypeName = TypeName(s"${caseClass.name}F")

        val params: List[ValDef] = caseClass.impl.body
          .collect({
            case v: ValDef if v.mods.hasFlag(PARAMACCESSOR) && v.mods.hasFlag(CASEACCESSOR) => v
          })
          .map(convertCaseClassParam)

        q"case class $Name[..${clait.tparams}, $A](..$params) extends $NonRecursiveAdtFullName"
      }

      val NonRecursiveAdtCases: List[Tree] =
        AdtCases.map {
          case c: ModuleDef => convertCaseObject(c)
          case c: ClassDef  => convertCaseClass(c)
          case _            => sys.error("Nope")
        }

      val nonRecursiveAdt: ClassDef =
        q"""sealed trait $NonRecursiveAdtName[..${clait.tparams}, $A] extends Product with Serializable"""

      val fixieModule: ModuleDef = q"""
object fixie {
  $nonRecursiveAdt
  ..$NonRecursiveAdtCases
}
"""

      val outputs =
        clait match {
          case classOrTrait: ClassDef if isSealed(classOrTrait) =>
            List(
              clait,
              ModuleDef(
                companion.mods,
                companion.name,
                Template(
                  companion.impl.parents,
                  companion.impl.self,
                  companion.impl.body :+ fixieModule
                )
              )
            )

          case _ =>
            sys.error("@fixie should only annotate sealed traits or sealed abstract classes")
        }

      println(outputs)

      c.Expr[Any](Block(outputs, Literal(Constant(()))))
    }
  }

}
