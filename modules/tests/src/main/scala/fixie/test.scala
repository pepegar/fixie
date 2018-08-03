package fixie

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

  import MyIntList.fixie.{ConsF => ConsIntF, NilF => NilIntF, _}

  val sumList: Algebra[MyIntListF, Int] = Algebra {
    case ConsIntF(head, tail) => head + tail
    case NilIntF()            => 0
  }

  val sum = scheme.hylo(sumList.run, MyIntList.fixie.projectCoalgebra.run)

  val intList: MyIntList = MyIntList.Cons(1, MyIntList.Cons(2, MyIntList.Cons(3, MyIntList.Nil)))

  println(s"sum int list: " + sum(intList))

  import MyList.fixie._

  val concatList: Algebra[MyListF[String, ?], String] = Algebra {
    case ConsF(head, tail) => s"$head|$tail"
    case NilF()            => ""
  }

  val stringList = MyList.Cons("hello", MyList.Cons("dolly", MyList.Nil[String]()))

  val concat = scheme.hylo(concatList.run, MyList.fixie.projectCoalgebra[String].run)

  println(s"concat string list: " + concat(stringList))

}
