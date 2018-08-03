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

import qq.droste._

@fixie sealed trait MyList[A]
object MyList {
  case class Cons[A](head: A, tail: MyList[A]) extends MyList[A]
  case class Nil[A]()                          extends MyList[A]
}

@fixie sealed trait MyIntList
object MyIntList {
  case class Cons(head: Int, tail: MyIntList) extends MyIntList
  case object Nil                             extends MyIntList
}

object App extends App {

  import MyIntList.fixie._

  val sumList: Algebra[MyIntListF, Int] = Algebra {
    case ConsF(head, tail) => head + tail
    case NilF()            => 0
  }

  val sum = scheme.hylo(sumList.run, projectCoalgebra.run)

  val list: MyIntList = MyIntList.Cons(1, MyIntList.Cons(2, MyIntList.Cons(3, MyIntList.Nil)))

  println(s"sum : " + sum(list))

}
