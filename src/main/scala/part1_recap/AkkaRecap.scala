package part1_recap

import akka.actor.SupervisorStrategy.Restart
import akka.actor.SupervisorStrategy.Stop
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.OneForOneStrategy
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.Stash
import akka.actor.SupervisorStrategy
import akka.util
import akka.util.Timeout

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future

object AkkaRecap extends App {

  // actor ...
  val system = ActorSystem("AkkaRecap")
  // #1:
  val actor = system.actorOf(Props[SimpleActor], "simpleActor")

  // EC
  // implicit val ec: ExecutionContextExecutor = system.dispatcher
  // or:
  import system.dispatcher

  class SimpleActor extends Actor with ActorLogging with Stash {

    override def receive: Receive = {
      case "createChild" =>
        val childActor = context.actorOf(Props[SimpleActor], "myChild")
        childActor ! "hello from child"
      case "stashThis" =>
        stash()
      case "change handler NOW" =>
        unstashAll()
        context.become(anotherHandler)
      case msg => println(s"I received: $msg")
      // additionally
      case "change" => context.become(anotherHandler)
    }

    def anotherHandler: Receive = {
      case msg => println(s"In another receive handler: $msg")
    }

    override def preStart(): Unit =
      // from ActorLogging:
      // instead of : println("I'm starting")
      log.info("I'm starting")

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: RuntimeException => Restart
      case _                   => Stop
    }
  }
  // #2: sending message
  actor ! "hello akka"
  actor ! "hello akka2"
  actor ! "hello akka3"
  actor ! "hello akka4"
  actor ! "change"
  actor ! "createChild"

  // stop actors - context.stop
  actor ! PoisonPill

  // logging

  // supervision !!!

  // configure akka infrastructures:

  // schedulers
  import scala.concurrent.duration._
  // import system.dispatcher // already
  system.scheduler.scheduleOnce(2 seconds) {
    actor ! "delayed Happy birthday!"
  }

  // Akka patterns including Final State Machine FSM + ask pattern
  import akka.pattern.ask
  implicit val timeout: Timeout = Timeout(3 seconds)
  val future: Future[Any] = actor ? "question"

  // pipe pattern :
  import akka.pattern.pipe
  val anotherActor = system.actorOf(Props[SimpleActor], "anotherSimpleActor")
  future.mapTo[String].pipeTo(anotherActor) // when future complete then has been sent as a message to another actor
}
