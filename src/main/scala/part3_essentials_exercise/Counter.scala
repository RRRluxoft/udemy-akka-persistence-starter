package part3_essentials_exercise

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props

object Counter extends App {

  // DOMAIN of the counter
  object CounterActor {
    sealed trait Action[A]
    case object Increment extends Action[Unit]
    case object Decrement extends Action[Unit]
    case object Print extends Action[Unit]
  }

  class CounterActor extends Actor {
    import CounterActor._
    var number = 0
    def incr: Int => Unit = (_: Int) => number += 1
    def decr = number -= 1

    override def receive: Receive = {
      case Increment => incr(number)
      case Decrement => decr
      case Print     => println(s"[counter] Current #${number}")
    }
  }

  val system = ActorSystem("counterSystem")
  val counterActor = system.actorOf(Props[CounterActor], "counter")

  import CounterActor._
  counterActor ! Print
  counterActor ! Increment
  counterActor ! Print
  counterActor ! Increment
  counterActor ! Print
  counterActor ! Decrement
  counterActor ! Print
  counterActor ! Increment
  counterActor ! Print

}
