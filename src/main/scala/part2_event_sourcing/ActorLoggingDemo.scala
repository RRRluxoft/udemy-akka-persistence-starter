package part2_event_sourcing

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.event.LoggingAdapter

object ActorLoggingDemo extends App {

  class SimpleActorWithExplicitLogger extends Actor {
    val logger: LoggingAdapter = Logging(context.system, this)

    override def receive: Receive = {
      case msg => logger.info(msg.toString)
    }
  }

  class ActorWithLogging extends Actor with ActorLogging {

    override def receive: Receive = {
      case (a, b) => log.info(s"Just in case we have got {} and {}", a, b)
      case msg    => log.info(s"Here is the log: $msg from ActorLogging")
    }
  }

  val system = ActorSystem("LoggingDemoActor")
  val simpleLogging = system.actorOf(Props[SimpleActorWithExplicitLogger], "simpleLogging")

  simpleLogging ! "Look at this, kurcze!"

  val logging = system.actorOf(Props[ActorWithLogging], "actorLogging")

  logging ! (22, 17)
  logging ! "Simple\ttext"

}
