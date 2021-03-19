package part2_event_sourcing

import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.persistence.PersistentActor
import akka.persistence.SaveSnapshotFailure
import akka.persistence.SaveSnapshotSuccess
import akka.persistence.SnapshotOffer

import scala.collection.mutable

object Snapshots extends App {

  // commands
  case class ReceivedMessage(contents: String) // msg FROM your contact
  case class SentMessage(contents: String) // msg TO your contact
  // events
  case class ReceivedMessageRecord(id: Int, contents: String)
  case class SentMessageRecord(id: Int, contents: String)

  object Chat {
    def props(owner: String, contact: String) = Props(new Chat(owner, contact))
  }

  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {
    val MAX_MESSAGES = 10

    var currentMessageId = 0
    var commandsWithoutCheckpoint = 0
    val lastMessages = new mutable.Queue[(String, String)]

    override def persistenceId: String = s"$owner-$contact-chat"

    override def receiveCommand: Receive = {
      case ReceivedMessage(contents) =>
        persist(ReceivedMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Received msg $contents")
          maybeReplaceMessage(contact, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case SentMessage(contents) =>
        persist(SentMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Sent message : $contents")
          maybeReplaceMessage(owner, contents)
          currentMessageId += 1
        }
      case "print" =>
        log.info(s"Most recent messages: $lastMessages ")
      case SaveSnapshotSuccess(metadata)         => log.info(s"saving snapshot succeeded: $metadata")
      case SaveSnapshotFailure(metadata, reason) => log.warning(s"saving snapshot $metadata failed because of $reason")
    }

    override def receiveRecover: Receive = {
      case ReceivedMessageRecord(id, contents) =>
        log.info(s"Recovered received msg $id: $contents")
        maybeReplaceMessage(contact, contents)
        currentMessageId = id

      case SentMessageRecord(id, contents) =>
        log.info(s"Recovered sent msg $id: $contents")
        maybeReplaceMessage(owner, contents)
        currentMessageId = id

      case SnapshotOffer(metadata, contents) =>
        log.info(s"Recovered snapshot $metadata")
        //      instead of :  lastMessages = contents
        contents.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
    }

    def maybeReplaceMessage(sender: String, contents: String) = {
      if (lastMessages.size >= MAX_MESSAGES) lastMessages.dequeue
      lastMessages.enqueue((sender, contents))
    }

    def maybeCheckpoint() = {
      commandsWithoutCheckpoint += 1
      if (commandsWithoutCheckpoint >= MAX_MESSAGES) {
        log.info(s"Saving checkpoint...")
        saveSnapshot(lastMessages)
        commandsWithoutCheckpoint = 0
      }
    }
  }

  val system = ActorSystem("SnapshotDemo")
  val chat = system.actorOf(Chat.props("dan123", "martin345"))

//  for (i <- 1 to 100000) {
//    chat ! ReceivedMessage(s"akka rocks $i")
//    chat ! SentMessage(s"Akka rules $i")
//  }

  chat ! "print"
}
