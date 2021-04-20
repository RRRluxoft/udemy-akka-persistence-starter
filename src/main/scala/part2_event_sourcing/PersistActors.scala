package part2_event_sourcing

import java.util.Date
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.actor.Props
import akka.persistence.PersistentActor

object PersistActors extends App {

  /** Scenario: we have a business and an accountant which keeps track of our invoices.
    */
  // COMMANDS
  case class Invoice(recipient: String, date: Date, amount: Int)
  case class InvoiceBulk(invoices: List[Invoice])
  case object Shutdown

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
        /**
          When you receive a command(Invoice)
          1) you create an EVENT(InvoiceRecorded) to persist into the store
          2) you persist the EVENT, the pass in a callback that will get triggered once yhe event is written
          3) we update the actor's state when the event has persisted
          */
        log.info(s"Receive invoice for amount: $amount")
        val event = InvoiceRecorded(latestInvoiceId, recipient, date, amount)
        persist(event) /**time gap here: all msgs sent to this actor are STASHED*/ { event =>
          // SAFE to access mutable state here
          // update state
          latestInvoiceId += 1
          totalAmount += amount

          sender() ! "PersistenceACK"

          log.info(s"Persisted $event as invoice #${event.id}, for total amount ${totalAmount}")
        }
      case InvoiceBulk(invoices) =>
        /*
        1. create events (plural)
        2. persist all events
        3. update the actor state when each event is persisted
         */
        val invoicesIds: Seq[Int] = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events = invoices.zip(invoicesIds).map { pair: (Invoice, Int) =>
          val id = pair._2
          val invoice = pair._1
          InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
        }
        persistAll(events) { event: InvoiceRecorded =>
          latestInvoiceId += 1
          totalAmount += event.amount
          log.info(s"Persisted SINGLE $event as invoice #${event.id}, for total amount $totalAmount")
        }
      case Shutdown =>
        context.stop(self)

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
        * best practice: follow the logic in the persist steps of receiveCommand
        * @see #Accountant.receiveCommand
        */
      case InvoiceRecorded(id, _, _, amount) =>
        latestInvoiceId = id
        totalAmount += amount
        log.info(s"Recovered invoice #$id for amount $amount, total amount $totalAmount")
    }

    // If persisting failed
    // Actor will STOPPED.
    // start actor again after a while
    // use Backoff supervisor
    override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Fail to persist $event because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    // Called if JOURNAL fails to persist EVENT
    // Actor is RESUMED
    override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist rejected for $event because of $cause")
      super.onPersistRejected(cause, event, seqNr)
    }
  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")

//  for (i <- 1 to 10) {
//    accountant ! Invoice("The sofa Company", new Date, i * 1000)
//  }
//  accountant ! "print"

  /**
    Persistence failures:
    */
  /**
    * Persist multiple EVENT
    * persistAll
    */
  val newInvoices = for (i <- 1 to 5) yield { Invoice("The awesome chairs", new Date, i * 2000) }
//  accountant ! InvoiceBulk(newInvoices.toList)

  /**
    * NEVER EVER CALL PERSIST or PersistAll from Futures !!!
    */
  /**
    * Shutdown of persistent actor
    * Best practice: define your own "Shutdown" msgs :~)
    */
//  accountant ! PoisonPill // -- NEVER EVER USE IT !!!
  accountant ! Shutdown
}
