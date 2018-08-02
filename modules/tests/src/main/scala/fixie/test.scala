package fixie
// import cats._, cats.derived._

// sealed trait MyListF[A, REC]
// object MyList {
//   case class ConsF[A, REC](head: A, tail: REC) extends MyListF[A, REC]
//   case class NilF[A, REC]()                    extends MyListF[A, REC]

//   implicit val fc: Functor[({ type λ[α] = MyListF[Int, α] })#λ] = {
//     import auto.functor._
//     semi.functor
//   }
// }

// @fixie sealed trait MyList[A]
// object MyList {
//   case class Cons[A](head: A, tail: MyList[A]) extends MyList[A]
//   case class Nil[A]()                          extends MyList[A]
// }

@fixie sealed trait MyIntList
object MyIntList {
  case class Cons(head: Int, tail: MyIntList) extends MyIntList
  case object Nil                             extends MyIntList
}

object App extends App {}
