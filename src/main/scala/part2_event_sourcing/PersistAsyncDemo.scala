package part2_event_sourcing

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.persistence.PersistentActor

object PersistAsyncDemo extends App {

  //commands
  case class Command(contents: String)
  //events
  case class Event(contents: String)

  // maybe: via props? YES, cause best practice
  object CriticalStreamProcessor {
    def props(eventAggregator: ActorRef): Props = Props(new CriticalStreamProcessor(eventAggregator))
  }

  class CriticalStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {
    override def persistenceId: String = "critical-stream-processor"

    override def receiveCommand: Receive = {
      case Command(contents) =>
        eventAggregator ! s"Processing $contents"
        persistAsync(Event(contents)) /* Time gap here so big */ { event =>
          log.info(s"Persisted $event")
          eventAggregator ! event
        }

        // some actual computation:
        val processedContents = contents + "_processed"
        persistAsync(Event(processedContents)) /* Time gap here so big */ { event =>
          log.info(s"Persisted processed $event")
          eventAggregator ! event
        }
    }

    override def receiveRecover: Receive = {
      case msg => log.info(s"Recovered $msg")
    }
  }

  class EventAggregator extends Actor with ActorLogging {

    override def receive: Receive = {
      case msg             => log.info(s"$msg")
      case Event(contents) =>
    }
  }

  val system = ActorSystem("PersistAsyncDemo")
  val eventAggregator = system.actorOf(Props[EventAggregator], "eventAggregator")
  val streamProcessor = system.actorOf(CriticalStreamProcessor.props(eventAggregator), "streamProcessor")

  streamProcessor ! Command("command 1")
  streamProcessor ! Command("command 2")

  /*
    persistAsync(+) vs persist(-)
    - performance: high-throughput envs

    persist(+) vs persistAsync(-)
    - ordering guaranties
 */

}
