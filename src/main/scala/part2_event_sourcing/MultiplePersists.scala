package part2_event_sourcing

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.persistence.PersistentActor

import java.util.Date

object MultiplePersists extends App {

  /**
    * Diligent accountant: with every invoice, will persist TWO events
    * - a tax record for the fiscal authority
    * - an invoice record for personal logs or some auditing authority
    */
  // COMMAND
  case class Invoice(recipient: String, date: Date, amount: Int)

  // EVENTS
  case class TaxRecord(taxId: String, recordId: Int, date: Date, totalAmount: Int)
  case class InvoiceRecord(invoiceRecordId: Int, recipient: String, date: Date, amount: Int)

  object DiligentAccountant {
    def props(taxId: String, taxAuthority: ActorRef): Props = Props(new DiligentAccountant(taxId, taxAuthority))
  }

  class DiligentAccountant(taxId: String, taxAuthority: ActorRef) extends PersistentActor with ActorLogging {
    var latestTaxRecordId = 0
    var latestInvoiceRecordId = 0

    override def persistenceId: String = "diligent-accountant"

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        persist(TaxRecord(taxId, latestTaxRecordId, date, amount / 3)) { record =>
          taxAuthority ! record
          latestTaxRecordId += 1 // 1 ordered !!!

          // 3
          persist("I herby declare this tax Record to be true and complete.")(declaration => taxAuthority ! declaration)
        }
        persist(InvoiceRecord(latestInvoiceRecordId, recipient, date, amount)) { invoice =>
          taxAuthority ! invoice // 2
          latestInvoiceRecordId += 1

          // 4
          persist("I herby declare this tax Invoice to be true .")(declaration => taxAuthority ! declaration)
        }
    }

    override def receiveRecover: Receive = {
      case event => log.info(s"Recovered : $event")
    }

  }

  class TaxAuthority extends Actor with ActorLogging {

    override def receive: Receive = {
      case msg => log.info(s"Received : $msg")
    }
  }

  val system = ActorSystem("MultiplePersistsDemo")
  val taxAuthority = system.actorOf(Props[TaxAuthority], "HMRC")
  val accountant = system.actorOf(DiligentAccountant.props("UK52325", taxAuthority))

  accountant ! Invoice("The Sofa Company", new Date, 2000)

}
