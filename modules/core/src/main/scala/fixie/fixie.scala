package fixie

import scala.reflect.macros.blackbox
import scala.language.experimental.macros
import scala.annotation.StaticAnnotation
import scala.annotation.compileTimeOnly

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

    val A: TypeName                     = c.freshName(TypeName("A"))
    val claitTypeParams                 = clait.tparams
    val claitTypeParamNames             = claitTypeParams.map(_.name)
    val claitTypeParamNamesAsTrees      = claitTypeParams.map(x => Ident(x.name))
    val claitTypeParamNamesWithA        = claitTypeParamNames :+ A
    val claitTypeParamNamesWithAAsTrees = claitTypeParamNamesWithA.map(x => Ident(x))

    val NonRecursiveAdtName: TypeName = TypeName(s"${clait.name}F")
    val NonRecursiveAdtFullName: AppliedTypeTree =
      AppliedTypeTree(Ident(NonRecursiveAdtName), claitTypeParamNamesWithAAsTrees)

    def isSealed(classOrTrait: ClassDef): Boolean =
      classOrTrait.mods.hasFlag(TRAIT) ||
        classOrTrait.mods.hasFlag(ABSTRACT) &&
          classOrTrait.mods.hasFlag(SEALED)

    val isCase: PartialFunction[Tree, Tree] = {
      case c: ClassDef if c.mods.hasFlag(CASE)  => c
      case c: ModuleDef if c.mods.hasFlag(CASE) => c
    }

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

    def getCaseClassParams(caseClass: ClassDef): List[ValDef] =
      caseClass.impl.body
        .collect({
          case v: ValDef if v.mods.hasFlag(PARAMACCESSOR) && v.mods.hasFlag(CASEACCESSOR) => v
        })
        .map(convertCaseClassParam)

    def convertCaseClass(caseClass: ClassDef): ClassDef = {
      val Name: TypeName = TypeName(s"${caseClass.name}F")

      val params: List[ValDef] = getCaseClassParams(caseClass)

      q"case class $Name[..${clait.tparams}, $A](..$params) extends $NonRecursiveAdtFullName"
    }

    val NonRecursiveAdtCases: List[ClassDef] =
      AdtCases.map {
        case c: ModuleDef => convertCaseObject(c)
        case c: ClassDef  => convertCaseClass(c)
        case _            => sys.error("Nope")
      }

    val nonRecursiveAdt: ClassDef =
      q"""sealed trait $NonRecursiveAdtName[..${clait.tparams}, $A] extends Product with Serializable"""

    val functorInstance = {
      q"""
implicit def functorInstance[..${clait.tparams}]: cats.Functor[({ type λ[α] = $NonRecursiveAdtName[..${clait.tparams
        .map(_.name)}, α] })#λ] = {
  import cats.derived._, auto.functor._
  semi.functor
}
"""
    }

    val toRecursive: ValDef = {
      val embedAlgebraCases: List[CaseDef] =
        (NonRecursiveAdtCases zip AdtCases) map {
          case (origin, target: ClassDef) =>
            val originName = TermName(origin.name.toString)
            val targetName = TermName(target.name.toString)
            val freshTerms = List.fill(getCaseClassParams(target).length)(TermName(c.freshName))
            val binds      = freshTerms.map(x => Bind(x, Ident(termNames.WILDCARD)))
            val args       = freshTerms.map(x => Ident(x))
            cq"$originName(..$binds) => $targetName[..$claitTypeParamNamesAsTrees](..$args)"
          case (origin, target: ModuleDef) =>
            val originName = TermName(origin.name.toString)
            val targetName = TermName(target.name.toString)
            cq"$originName => $targetName"
        }

      val mtch = Match(EmptyTree, embedAlgebraCases)

      val algebra = q"""
new qq.droste.GAlgebra[$NonRecursiveAdtName, ${clait.name}, ${clait.name}]($mtch)
"""
      q"val embedAlgebra: qq.droste.Algebra[$NonRecursiveAdtName, ${clait.name}] = $algebra"

    }

    val toFixedPoint: ValDef = {
      val embedAlgebraCases: List[CaseDef] =
        (AdtCases zip NonRecursiveAdtCases) map {
          case (origin: ClassDef, target) =>
            val originName = TermName(origin.name.toString)
            val targetName = TermName(target.name.toString)
            val freshTerms = List.fill(getCaseClassParams(target).length)(TermName(c.freshName))
            val binds      = freshTerms.map(x => Bind(x, Ident(termNames.WILDCARD)))
            val args       = freshTerms.map(x => Ident(x))

            cq"$originName(..$binds) => $targetName[..$claitTypeParamNamesAsTrees, ${clait.name}](..$args)"
          case (origin: ModuleDef, target) =>
            val targetName = TermName(target.name.toString)
            val originName = TermName(origin.name.toString)
            cq"$originName => $targetName[..$claitTypeParamNamesAsTrees, ${clait.name}]()"
        }

      val mtch = c.untypecheck(Match(EmptyTree, embedAlgebraCases))

      val algebra = q"""
new qq.droste.GCoalgebra[$NonRecursiveAdtName, ${clait.name}, ${clait.name}]($mtch)
"""
      q"val projectCoalgebra: qq.droste.Coalgebra[$NonRecursiveAdtName, ${clait.name}] = $algebra"

    }

    val fixieModule: ModuleDef = q"""
object fixie {
  $nonRecursiveAdt
  ..$NonRecursiveAdtCases
  $functorInstance
  $toFixedPoint
  $toRecursive
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

    c.Expr[Any](Block(outputs, Literal(Constant(()))))
  }
}
