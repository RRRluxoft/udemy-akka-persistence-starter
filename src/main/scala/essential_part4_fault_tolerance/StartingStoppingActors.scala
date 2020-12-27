package essential_part4_fault_tolerance

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Kill
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.Terminated

object StartingStoppingActors extends App {

  val system = ActorSystem("StoppingActorsDemo")

  object Parent {
    case class StartChild(name: String)
    case class StopChild(name: String)
    case object Stop
  }

  class Parent extends Actor with ActorLogging {
    import Parent._
    override def receive: Receive = withChild(Map[String, ActorRef]())

    def withChild(children: Map[String, ActorRef]): Receive = {
      case StartChild(name) =>
        log.info(s"Starting child $name")
        context.become(withChild(children + (name -> context.actorOf(Props[Child], name))))
      case StopChild(name) =>
        log.info(s"Stopping child : $name")
        val child: Option[ActorRef] = children.get(name)
        child.foreach(context.stop)
      case Stop =>
        log.info(s"Stopping myself")
        context.stop(self)
      case msg =>
        log.info(msg.toString)
    }
  }

  class Child extends Actor with ActorLogging {

    override def receive: Receive = {
      case msg => log.info(msg.toString)
    }
  }

  /*
  #1 - using context.stop
   */
  import Parent._
  /*  val parent = system.actorOf(Props[Parent], "parent")
  parent ! StartChild("child-1")

  val child = system.actorSelection("/user/parent/child-1")
  child ! "Hi there )"

  parent ! StopChild("child-1") // it doesn't mean STOPPED immediately
//  for (_ <- 1 to 10) child ! "are you still there, kid?"

  parent ! StartChild("child-2")
  val child2 = system.actorSelection("/user/parent/child-2")
  child2 ! "hi second child"

  parent ! Stop
  for (_ <- 1 to 10) parent ! "parent, are you still there?"
  for (i <- 1 to 100) child2 ! s"[$i] second kid, are you still alive?"
   */

  /*
  #2 - PoisonPill or even Kill
   */
  /*
  parent ! PoisonPill
  parent ! Kill // brutal - throw an Exception
   */

  /*
  #3 - context.watch
   */

  class Watcher extends Actor with ActorLogging {
    import Parent._

    override def receive: Receive = {
      case StartChild(name) =>
        log.info(s"start to watch for $name")
        context.watch(context.actorOf(Props[Child], name))
      case Terminated(ref) =>
        log.info(s"the ref that I'm watching: $ref has been stopped")
    }
  }

  val watcher = system.actorOf(Props[Watcher], "watcher")
  watcher ! StartChild("watchedChild")
  val watchedChild = system.actorSelection("/user/watcher/watchedChild")

  Thread.sleep(500L)
  watchedChild ! PoisonPill
}
