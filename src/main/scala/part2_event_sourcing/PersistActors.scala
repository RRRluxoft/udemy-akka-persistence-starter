package part2_event_sourcing

import java.util.Date

import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.persistence.PersistentActor

object PersistActors extends App {

  /*
  Scenario: we have a business and an accountant which keeps track of our invoices.
   */

  // COMMANDS
  case class Invoice(recipient: String, date: Date, amount: Int)

  // EVENTS
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)

  class Accountant extends PersistentActor with ActorLogging {

    var latestInvoiceId = 0
    var totalAmount = 0

    override def persistenceId: String = "simple-accountant" // unique name

    /**
      * The "normal" receive method
      * @return
      */
    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        /*
      When you receive a command(Invoice)
      1) you create an EVENT(InvoiceRecorded) to persist into the store
      2) you persist the EVENT, the pass in a callback that will get triggered once yhe event is written
      3) we update the actor's state when the event has persisted
         */
        log.info(s"Receive invoice for amount: $amount")
        val event = InvoiceRecorded(latestInvoiceId, recipient, date, amount)
        persist(event) /*time gap here: all msgs sent to this actor are STASHED*/ { event =>
          // SAFE to access mutable state here
          // update state
          latestInvoiceId += 1
          totalAmount += amount

          sender() ! "PersistenceACK"

          log.info(s"Persisted $event as invoice #${event.id}, for total amount ${totalAmount}")
        }
      // act like a normal actor
      case "print" =>
        log.info(s"Latest invoice id : $latestInvoiceId, total amount $totalAmount")
    }

    /**
      * Handler that will be called on recovery
      * @return
      */
    override def receiveRecover: Receive = {

      /**
      best practice: follow the logic in the persist steps of receiveCommand
      @see #Accountant.receiveCommand
        */
      case InvoiceRecorded(id, _, _, amount) =>
        latestInvoiceId = id
        totalAmount += amount
        log.info(s"Recovered invoice #$id for amount $amount, total amount $totalAmount")
      case _ => log.warning(s"Nothing to restore anymore")
    }
  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")

  for (i <- 1 to 10) {
    accountant ! Invoice("The sofa Company", new Date, i * 1000)
  }
//  accountant ! "print"

  /*

 */

}
