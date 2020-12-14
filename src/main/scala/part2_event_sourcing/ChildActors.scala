package part2_event_sourcing

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props

object ChildActors extends App {

  //DOMAIN
  object Parent {
    case class CreateChild(name: String)
    case class TellChild(msg: String)
  }

  class Parent extends Actor {
    import Parent._
//    var child: ActorRef = null

    override def receive: Receive = {
      case CreateChild(name) =>
        println(s"${self.path} creating child")
        val childRef = context.actorOf(Props[Child], name)
//        child = childRef
        context.become(withChild(childRef))
    }

    // add here:
    def withChild(childRef: ActorRef): Receive = {
      case TellChild(msg) => childRef forward msg
    }

  }

  class Child extends Actor {

    override def receive: Receive = {
      case msg => println(s"${self.path} I got: $msg")
    }
  }

  val system = ActorSystem("ParentChildActorSystem")
  val parent = system.actorOf(Props[Parent], "parent")
  import Parent._
  parent ! CreateChild("myChild")
  parent ! TellChild("hello message")

  /**
    * Actor selection:
    */
  val childSelection = system.actorSelection("/user/parent/myChild")
  val childSelectionActorPath = system.actorSelection(ChildActors.parent.path)
  childSelection ! "I found you!"
  childSelectionActorPath ! TellChild("here")
}
