package com.pepegar

import fixie._

@fixie sealed trait MyIntList
object MyIntList {
  case class Cons(head: Int, tail: MyIntList) extends MyIntList
  case object Nil                             extends MyIntList
}
// @fixie sealed trait MyList[A]
// object MyList {
//   case class Cons[A](head: A, tail: MyList[A]) extends MyList[A]
//   case class Nil[A]()                          extends MyList[A]
// }

object App extends App {

  println(MyIntList.fixie)

}
