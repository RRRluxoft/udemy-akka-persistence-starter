package part1_recap


import scala.concurrent.Future
import scala.util.{Failure, Success}

object ScalaRecap extends App {

  println("Scala recap")

  // OO
  class Animal
  trait Carnivore { def eat(a: Animal): Unit }
  object Carnivore

  // FP
  val anIncrement: Int => Option[Int] = x => Option(x + 1)
  println(s"an increment of 7: ${anIncrement(7)}")
  val incList = List(1,2,3).map(anIncrement)
  println(s"list increment : ${incList}")

  // HOF: flatMap, filter
  // for-comprehensions
  for {
    op <- incList
    i  <- op
  } yield println(i)
  val newList = incList.flatMap(op =>
    op.flatMap(anIncrement)
  )
  println(newList)

  println(s"flatMap :  ${ Some(11).flatMap( i => {
    val res = i + 2
    Option(res)
  }) }")

  // Partial function:
  def partialFunc(x: Int) = x match {
    case 0 => "Win"
    case 2 => "Twins"
    case _ => "Unknown"
  }

  // multithreading :
  import scala.concurrent.ExecutionContext.Implicits.global
  val future = Future { 42 }

  future.onComplete {
    case Success(value) => println(s"I got it : $value")
    case Failure(ex) => println(s"We got the $ex")
  } // on SOME other thread

  val partialFunction: PartialFunction[Int, Int] = {
    case 1 => 42
    case 2  => 65
    case _ => 999
  }

  println(incList.flatMap(e =>
    e.flatMap( i =>
      Option(partialFunction(i)))))

  // type Aliases
  type AkkReceive = PartialFunction[Any, Unit]
  def receive: AkkReceive = {
    case 2 => println("hello!")
    case _ => println("confused ...")
  }


  // conversions :
  // implicit method :
  case class Person(name: String) {
    def greet: String = s"Hi, Im a $name"
  }
  implicit def fromStringToPerson(in: String): Person = Person(in)
  println("Peter".greet)

  // implicit classes
  implicit class Dog(name: String) {
    def bark(): Unit = println("Bark, bark!!!")
  }
  "Doggy".bark // new Dog("Doggy").bark


  // implicit organization :
  implicit val numberOrdering: Ordering[Int] = Ordering.fromLessThan(_ > _)
  println(List(7,8,9).sortBy(identity)(numberOrdering))
  println(List(7,8,9).sorted)

  // imported scope :
  object Person {
    implicit val personOrdering: Ordering[Person] = Ordering.fromLessThan((p1, p2) => p1.name.compareTo(p2.name) < 0)
  }
  val pList = List(Person("Bob"), Person("Tom"), Person("Alice"))
  println(pList.sorted)
  println(pList.sorted(Person.personOrdering)) // the SAME


}
