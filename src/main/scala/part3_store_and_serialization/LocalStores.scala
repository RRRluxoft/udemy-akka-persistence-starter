package part3_store_and_serialization

import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.persistence.PersistentActor
import akka.persistence.RecoveryCompleted
import akka.persistence.SaveSnapshotFailure
import akka.persistence.SaveSnapshotSuccess
import akka.persistence.SnapshotOffer
import com.typesafe.config.ConfigFactory

object LocalStores extends App {

  class SimplePersistentActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "simple-persistent-actor"

    // mutable state
    var nMessages = 0 // WTF?!
    override def receiveCommand: Receive = { //onMsg(0)
      case "print" =>
        log.info(s"I have persisted $nMessages so far")
      case "snap" =>
        saveSnapshot(nMessages)
      case SaveSnapshotSuccess(metadata) =>
        log.info(s"Save snapshot was successful: $metadata")
      case SaveSnapshotFailure(_, cause) =>
        log.warning(s"Save snapshot failed: $cause")
      case message =>
        persist(message) { _ =>
          log.info(s"Persisting $message")
          nMessages += 1
        }
    }

    def onMsg(nMsgs: Int): Receive = {
      case RecoveryCompleted =>
        log.info(s"Recovery DONE")
      case "print" =>
        log.info(s"I've persisted $nMsgs so far")
      case "snap" =>
        saveSnapshot(nMsgs)
      case msg =>
        persist(msg) { _ =>
          log.info(s"Persisted $msg")
          context become (onMsg(nMsgs + 1))
        }
    }

    override def receiveRecover: Receive = {
      case RecoveryCompleted =>
        log.info("Recovery done")
      case SnapshotOffer(_, payload: Int) =>
        log.info(s"Recovered snapshot: $payload")
        nMessages = payload
      case message =>
        log.info(s"Recovered: $message")
        nMessages += 1
    }
  }

  val localStoreActorSystem = ActorSystem("localStoreActorSystem", ConfigFactory.load().getConfig("localStores"))
  val persistentActor = localStoreActorSystem.actorOf(Props[SimplePersistentActor], "persistentActor")

  for (i <- 1 to 10) {
    persistentActor ! s"I love Akka [${i}]"
  }

  persistentActor ! "print"
  persistentActor ! "snap"

  for (i <- 11 to 20) {
    persistentActor ! s"I love Akka [${i}]"
  }

}
