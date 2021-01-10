package essential_part5_infra

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.actor.Props
import akka.dispatch.ControlMessage
import akka.dispatch.PriorityGenerator
import akka.dispatch.UnboundedStablePriorityMailbox
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object Mailboxes extends App {

  val system = ActorSystem("mailboxesDemo", ConfigFactory.load().getConfig("mailboxesDemo"))

  class SimpleActor extends Actor with ActorLogging {

    override def receive: Receive = {
      case msg => log.info(msg.toString)
    }
  }

  /**
    * case #1 - custom priority mailbox
    * P0  -> most important
    * P1
    * P2
    * P3
    */

  // step1 - mailbox definition
  class SupportTicketPriorityMailbox(setting: ActorSystem.Settings, config: Config)
    extends UnboundedStablePriorityMailbox(
      PriorityGenerator {
        case msg: String if msg.startsWith("[PO]") => 0
        case msg: String if msg.startsWith("[P1]") => 1
        case msg: String if msg.startsWith("[P2]") => 2
        case msg: String if msg.startsWith("[P3]") => 3
        case _                                     => 4
      }
    )

  // step 2 - make it known in the application.conf
  // step 3 - attach the dispatcher to an actor
  val supportTicketLogger = system.actorOf(Props[SimpleActor].withDispatcher("support-ticket-dispatcher"))
//  supportTicketLogger ! PoisonPill
//  supportTicketLogger ! "[P3] this thing would be nice to have"
//  supportTicketLogger ! "[P0] this needs to be solve NOW!"
//  supportTicketLogger ! "[P0] this needs to be solve NOW 2!"
//  supportTicketLogger ! "[P1] do this when you have the time"

  /**
    * case #2 - control-aware mailbox
    * we'll use UnboundedControlAwwareMailbox
    */
  // step 1 - mark important msgs as control msg
  case object ManagementTicket extends ControlMessage
  // step 2 - configure who gets the mailbox
  // - make the actor attach to the mailbox
  // method #1
  val controlAwareActor = system.actorOf(Props[SimpleActor].withMailbox("control-mailbox"))
  controlAwareActor ! "[P3] this thing would be nice to have"
  controlAwareActor ! "[P0] this needs to be solve NOW!"
  controlAwareActor ! "[P1] do this when you have the time"
  controlAwareActor ! ManagementTicket

  // method #2 - using deployment config
  val altControlAwareActor = system.actorOf(Props[SimpleActor], "altControlAwareActor")
  altControlAwareActor ! "[P3] this thing would be nice to have"
  altControlAwareActor ! "[P0] this needs to be solve NOW!"
  altControlAwareActor ! "[P1] do this when you have the time"
  controlAwareActor ! ManagementTicket
}
