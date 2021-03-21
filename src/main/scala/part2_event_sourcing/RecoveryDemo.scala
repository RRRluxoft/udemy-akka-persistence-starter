package part2_event_sourcing

import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.persistence.PersistentActor
import akka.persistence.Recovery
import akka.persistence.RecoveryCompleted
import akka.persistence.SnapshotSelectionCriteria

object RecoveryDemo extends App {

  case class Command(contents: String)
  case class Event(id: Int, contents: String)

  class RecoveryActor extends PersistentActor with ActorLogging {

    override def persistenceId: String = "recovery-actor"

    // Make it stateless #0
//    var latestPersistedEventId = 0

    // Make it stateless #3
    override def receiveCommand: Receive = onLine(0)

    // Make it stateless #1
    def onLine(latestPersistedEventId: Int): Receive = {
      case Command(contents) =>
        persist(Event(latestPersistedEventId, contents)) { event =>
          log.info(s"Persisted event: $event, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished. ")
        }
        // Make it stateless #2
        context become onLine(latestPersistedEventId + 1)
    }

    override def receiveRecover: Receive = {
      // additional initialization
      // this USEFUL !
      case RecoveryCompleted =>
        log.info(s"Recovering completed")
      case Event(id, contents) =>
//        if (contents.contains("314"))
//          throw new RuntimeException("I cant anymore!!!")
        log.info(s"Recovered: $contents, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished. ")
        /*
        this will NOT change the event handler during recovery !
        AFTER recovery the 'normal' handler will be the result of ALL the stacking of context.become
         */
        context become onLine(id + 1) // this...
    }

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error("I failed at recovery")
      super.onRecoveryFailure(cause, event)
    }

//    override def recovery: Recovery = Recovery(toSequenceNr = 100) // - DONT persist more events after a customized recovery
//    override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
//    override def recovery: Recovery = Recovery.none
  }

  val system = ActorSystem("RecoveryDemo")
  val recoveryActor = system.actorOf(Props[RecoveryActor], "recoveryActor")

  /*
  Stashing commands
   */
  for (i <- 1 to 1000) {
    recoveryActor ! Command(s"command $i")
  }

  // ALL COMMANDS SENT DURING RECOVERY ARE STASHED
  /*
  2 - failure during recovery
    - onRecoveryFailure + the actor is STOPPED

  3 - customizing recovery
    - DONT persist more events after a customized recovery

  4 - recovery status or KNOWING when you're done recovering
    - getting a signal when you're DONE recovering

  5 - stateless actors
    -
   */
  recoveryActor ! Command(s"special command 1")
  recoveryActor ! Command(s"special command 2")

}
