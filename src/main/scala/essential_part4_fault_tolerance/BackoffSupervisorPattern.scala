package essential_part4_fault_tolerance

import akka.actor.SupervisorStrategy.Stop
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.OneForOneStrategy
import akka.actor.Props
import akka.pattern.Backoff
import akka.pattern.BackoffSupervisor

import scala.concurrent.duration._
import java.io.File
import scala.io.Source

object BackoffSupervisorPattern extends App {

  case object ReadFile

  class FileBasedPersistentActor extends Actor with ActorLogging {

    var source: Source = null

    override def preStart(): Unit =
      log.info("Persistent actor starting")

    override def postStop(): Unit =
      log.warning("Persistent actor has stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit =
      log.warning("Persistent actor restarting")

    override def receive: Receive = {
      case ReadFile =>
        if (source == null) source = Source.fromFile(new File("src/main/resources/testfiles/important_data.txt"))
        log.info(s"I've just read some data: ${source.getLines().toList}")
    }
  }

  val system = ActorSystem("BackoffSupervisorDemo")
//  val simpleActor = system.actorOf(Props[FileBasedPersistentActor], "simpleActor")
//  simpleActor ! ReadFile

  val simpleSupervisorProps = BackoffSupervisor.props(
    Backoff.onFailure(
      Props[FileBasedPersistentActor],
      "simpleBackoffActor",
      3 seconds,
      30 seconds,
      0.2
    )
  )

//  val simpleBackoffSupervisor = system.actorOf(simpleSupervisorProps, "simpleSupervisor")
//  simpleBackoffSupervisor ! ReadFile

  val stopSupervisorProps = BackoffSupervisor.props(
    Backoff
      .onFailure(
        Props[FileBasedPersistentActor],
        "stopBackoffActor",
        3 seconds,
        30 seconds,
        0.2
      )
      .withSupervisorStrategy(
        OneForOneStrategy() {
          case _ => Stop
        }
      )
  )
//  val stopBackoffSupervisor = system.actorOf(stopSupervisorProps, "stopSupervisor")
//  stopBackoffSupervisor ! ReadFile

  class EagerFBPActor extends FileBasedPersistentActor {

    override def preStart(): Unit = {
      log.info(s"Eager actor starting")
      source = Source.fromFile(new File("src/main/resources/testfiles/important_data.txt"))
    }
  }

//  val eagerActor = system.actorOf(Props[EagerFBPActor], "eagerActor")
  // ActorInitializationException => STOP
  val repeatedSupervisorProps = BackoffSupervisor.props(
    Backoff.onStop(
      Props[EagerFBPActor],
      "eagerActor",
      1 second,
      30 seconds,
      0.1
    )
  )
  val repeatedSupervisor = system.actorOf(repeatedSupervisorProps, "eagerSupervisor")

}
