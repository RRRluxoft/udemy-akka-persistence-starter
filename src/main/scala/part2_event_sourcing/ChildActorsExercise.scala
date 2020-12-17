package part2_event_sourcing

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props

object ChildActorsExercise extends App {

  // Distributed word counting
  object WorldCounterMaster {
    val regex: String = " "
    case class Initialize(nChildren: Int)
    case class WordCountTask(id: Int, text: String)
    case class WordCountReply(id: Int, count: Int)
  }

  class WorldCounterMaster extends Actor {
    import WorldCounterMaster._

    override def receive: Receive = {
      case Initialize(number) => //(1 to number).foreach(n => context.actorOf(Props[WorldCounterWorker], s"worker-${n}"))
        println(s"[master] initializing...")
        val childrenRefs: Seq[ActorRef] = for {
          n <- 1 to number
        } yield context.actorOf(Props[WorldCounterWorker], s"worker-${n}")
        context.become(withChildren(childrenRefs, 0, 0, Map[Int, ActorRef]()))
    }

    def withChildren(children: Seq[ActorRef], currentChildIndex: Int, currentTaskId: Int, requestMap: Map[Int, ActorRef]): Receive = {
      case text: String =>
        println(s"[master] I've received: $text - I'll send it to child $currentChildIndex")
        val originalSender = sender()
        val task = WordCountTask(currentTaskId, text)
        val childRef = children(currentChildIndex)
        childRef ! task
        val nextChildIndex = (currentChildIndex + 1) % children.length
        val newTaskId = currentTaskId + 1
        val newRequestMap = requestMap + (currentTaskId -> originalSender)
        context.become(withChildren(children, nextChildIndex, newTaskId, newRequestMap))
      case WordCountReply(id, count) =>
        //sender() !  - NOT REALLY . We need to send it to parent of that sender
        println(s"[master] received a replay task id-$id with $count words")
        val originalSender = requestMap(id)
        originalSender ! count
        context.become(withChildren(children, currentChildIndex, currentTaskId, requestMap - id))
    }
  }

  object WorldCounterWorker {}

  class WorldCounterWorker extends Actor {
    import WorldCounterMaster._

    override def receive: Receive = {
      case WordCountTask(id, text) =>
        println(s"[child] ${self.path} I have received task id-$id with $text ")
        sender() ! WordCountReply(id, text.split(regex).length)
    }
  }

  /*
  create master
  send Initialize(10) to master
  send "Akka is awesome" to master
    master will send a task("...") to one of its children
      child replies with a WordCountReply(3) to the master
    master replies with '3' to the sender

  requester -> master -> worker
  requester <- master <-
   */

  class TestActor extends Actor {
    import WorldCounterMaster._

    override def receive: Receive = {
      case "go" =>
        val master = context.actorOf(Props[WorldCounterMaster], "theMaster")
        master ! Initialize(3)
        val text = List("I love Akka", "Scala is super dope", "yes", "me too", "power of the moment now")
        text.foreach(text => master ! text)

      case count: Int => println(s"[test actor] I received a reply: $count")
    }
  }

  val system = ActorSystem("multiChildSystem")
  val testActor = system.actorOf(Props[TestActor], "tester")
  testActor ! "go"
//  val master = system.actorOf(Props[WorldCounterMaster], "master")
//  import WorldCounterMaster._
//  master ! Initialize(5)
//  master ! "Akka is awesome"
}
