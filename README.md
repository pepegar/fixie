# fixie

Fixie is a small library for transforming recursive ADTs into type
parametrized ones.  This gives you the possibility to use recursion
schemes on traditional ADTs withouth the hassle of transforming it
manually, all the boilerplate is done by fixie.

Fixie uses on [cats][] for functional programming, [kittens][] for
typeclass derivation, and [droste][] for recursion schemes.

[cats]: https://github.com/typelevel/cats
[kittens]: https://github.com/typelevel/kittens
[droste]: https://github.com/andyscott/droste

## Motivation

The idea behind fixie is to provide an easy way of applying [recursion
schemes][droste] to recursive data types you already have in your
program.

## Example

Let's see this example of a list implemented recursively.

``` scala
import fixie._

@fixie sealed trait MyList[A]
object MyList {
  case class Cons[A](head: A, tail: MyList[A]) extends MyList[A]
  case class Nil[A]()                          extends MyList[A]
}
```

The `@fixie` annotation is transforming the previous code block in
something like the following:

``` scala
@fixie sealed trait MyList[A]
object fixie {
  sealed trait MyListF[A, A$macro$1]
  case class ConsF[A, A$macro$1](head: A, tail: A$macro$1) extends MyListF[A, A$macro$1]
  case class NilF[A, A$macro$1] extends MyListF[A, A$macro$1]
  
  // cats.Functor instance, derived by Kittens
  implicit def functorInstance[A]: cats.Functor[scala.AnyRef {
    type λ[α] = MyListF[A, α]
  }#λ] = {
    import cats.derived._
    import auto.functor._
    semi.functor
  }
  
  def projectCoalgebra[A]: qq.droste.Coalgebra[({type λ[α] = MyListF[A, α]})#λ, MyList[A]] =
    new qq.droste.GCoalgebra[({type λ[α] = MyListF[A, α]})#λ, MyList[A], MyList[A]] {
      case Cons((fresh$macro$4 @ _), (fresh$macro$5 @ _)) => ConsF[A, MyList[A]](fresh$macro$4, fresh$macro$5)
      case Nil() => NilF[A, MyList[A]]()
    }
  
  def embedAlgebra[A]: qq.droste.Algebra[({type λ[α] = MyListF[A, α]})#λ, MyList[A]] =
    new qq.droste.GAlgebra[({type λ[α] = MyListF[A, α]})#λ, MyList[A], MyList[A]] {
      case ConsF((fresh$macro$2 @ _), (fresh$macro$3 @ _)) => Cons[A](fresh$macro$2, fresh$macro$3)
      case NilF() => Nil[A]()
    }
  
  implicit def basisInstance[A]: qq.droste.Basis[({type λ[α] = MyListF[A, α]})#λ, MyList[A]] =
    qq.droste.Basis.Default(embedAlgebra, projectCoalgebra)
}
```

## Current limitations

1. Currently fixie doesn't work correctly on ADTs in which the
  recursion appears inside a type constructor as in `case class
  Case(values: List[Adt]) extends Adt`.  This shouldn't be ver
  difficult to implement, though.
  
2. Fixie can work on type parametrized ADTs, but expects them to be
  invariant.
