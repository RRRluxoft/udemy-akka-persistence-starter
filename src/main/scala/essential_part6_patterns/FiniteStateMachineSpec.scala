package essential_part6_patterns

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.FSM
import akka.actor.Props
import akka.persistence.PersistentActor
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OneInstancePerTest
import org.scalatest.WordSpecLike

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.DurationDouble

class FiniteStateMachineSpec
  extends TestKit(ActorSystem("FSMSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with OneInstancePerTest {

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  import FiniteStateMachineSpec._
  "A vending machine" should {
    runTestSuit(Props[VendingMachine])
  }

  "A vending machine FMS" should {
    runTestSuit(Props[VendingMachineFSM])
  }

  def runTestSuit(props: Props): Unit = {
    "error when not initialized" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! RequestProduct("meat and bear")
      expectMsg(VendingError("MachineNotInitialized"))
    }

    "report a product not available" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map(), Map())
      vendingMachine ! RequestProduct("meat and wine")
      expectMsg(VendingError("ProductNotAvailable"))
    }

    "throw a timeout if I dont insert money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("apple" -> 10), Map("apple" -> 1))
      vendingMachine ! RequestProduct("apple")
      expectMsg(Instruction("Please insert 1 USD"))
      within(1.5 seconds) {
        expectMsg(VendingError("RequestTimeout"))
      }
    }

    "handle the reception of partial money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("wine" -> 10), Map("wine" -> 10))
      vendingMachine ! RequestProduct("wine")
      expectMsg(Instruction("Please insert 10 USD"))

      vendingMachine ! ReceiveMoney(2)
      expectMsg(Instruction("Please insert 8 USD"))

      within(1.5 seconds) {
        expectMsg(VendingError("RequestTimeout"))
        expectMsg(GiveBackChange(2))
      }
    }

    "deliver the product ifI insert all the money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("wine" -> 10), Map("wine" -> 10))
      vendingMachine ! RequestProduct("wine")
      expectMsg(Instruction("Please insert 10 USD"))

      vendingMachine ! ReceiveMoney(10)
      expectMsg(Deliver("wine"))
    }

    "give back change and be able to request money for a new product" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("wine" -> 10), Map("wine" -> 10))
      vendingMachine ! RequestProduct("wine")
      expectMsg(Instruction("Please insert 10 USD"))

      vendingMachine ! ReceiveMoney(15)
      expectMsg(Deliver("wine"))
      expectMsg(GiveBackChange(5))

      vendingMachine ! RequestProduct("wine")
      expectMsg(Instruction("Please insert 10 USD"))
    }
  }

}

object FiniteStateMachineSpec {

  case class Initialize(inventory: Map[String, Int], prices: Map[String, Int])
  case class RequestProduct(product: String)

  case class Instruction(instruction: String)
  case class ReceiveMoney(amount: Int)
  case class Deliver(product: String)
  case class GiveBackChange(amount: Int)

  case class VendingError(reason: String)
  case object ReceiveMoneyTimeout

  class VendingMachine extends Actor with ActorLogging {
    implicit val ec: ExecutionContext = context.dispatcher

    override def receive: Receive = idle

    def idle: Receive = {
      case Initialize(inventory, prices) => context.become(operational(inventory, prices))
      case _                             => sender() ! VendingError("MachineNotInitialized")
    }

    def operational(inventory: Map[String, Int], prices: Map[String, Int]): Receive = {
      case RequestProduct(product) =>
        inventory.get(product) match {
          case None | Some(0) =>
            sender() ! VendingError("ProductNotAvailable")
          case Some(_) =>
            val price: Int = prices(product)
            sender() ! Instruction(s"Please insert $price USD")
            context.become(waitForMoney(inventory, prices, product, 0, startReceiveMoneyTimeoutSchedule, sender()))
        }
    }

    def waitForMoney(
      inventory: Map[String, Int],
      prices: Map[String, Int],
      product: String,
      money: Int,
      moneyTimeoutSchedule: Cancellable,
      requester: ActorRef
    ): Receive = {
      case ReceiveMoneyTimeout =>
        requester ! VendingError("RequestTimeout")
        if (money > 0) requester ! GiveBackChange(money)
        context.become(operational(inventory, prices))
      case ReceiveMoney(amount) =>
        moneyTimeoutSchedule.cancel()
        val price = prices(product)
        if (money + amount >= price) {
          // user buys product
          requester ! Deliver(product)
          // deliver the change
          if (money + amount - price > 0) requester ! GiveBackChange(money + amount - price)
          // updating inventory
          val newStock = inventory(product) - 1
          val newInventory = inventory + (product -> newStock)
          context.become(operational(newInventory, prices))

        } else {
          val remainingMoney = price - money - amount
          requester ! Instruction(s"Please insert $remainingMoney USD")
          context.become(
            waitForMoney(
              inventory,
              prices,
              product,
              money + amount, // changed
              startReceiveMoneyTimeoutSchedule,
              requester
            )
          )
        }
    }

    def startReceiveMoneyTimeoutSchedule: Cancellable = context.system.scheduler.scheduleOnce(1 second) {
      self ! ReceiveMoneyTimeout
    }
  }

  // step 1 - define the STATES and the data of the actor
  trait VendingState
  case object Idle extends VendingState
  case object Operational extends VendingState
  case object WaitForMoney extends VendingState

  trait VendingData
  case object Uninitialized extends VendingData
  case class Initialized(inventory: Map[String, Int], prices: Map[String, Int]) extends VendingData

  case class WaitForMoneyData(inventory: Map[String, Int], prices: Map[String, Int], product: String, money: Int, requester: ActorRef)
    extends VendingData

  class VendingMachineFSM extends FSM[VendingState, VendingData] {
    // we dont have a receive handler
    // an EVENT(msg, data)

    /**
      state, data
    event => state and data can be changed.

    state = Idle
    data = Uninitialized

    event(Initialize(Map("wine" -> 23), Map("wine" -> 10)))
      =>
      state = Operational
      data = Initialized(Map("wine" -> 23), Map("wine" -> 10))

    event(RequestProduct(wine))
      =>
      state = WaitForMoney
      data = WaitForMoneyData(Map("wine" -> 23), Map("wine" -> 10), wine, 0, R)

    event(ReceiveMoney(15))
      =>
      state = Operational
      data = Initialized(Map("wine" -> 22), Map("wine" -> 10))

      */
    startWith(Idle, Uninitialized)

    when(Idle) {
      case Event(Initialize(inventory, prices), Uninitialized) =>
        goto(Operational) using Initialized(inventory, prices)
      // equivalent with context.become(operational(inventory, prices))

      case _ =>
        sender() ! VendingError("MachineNotInitialized")
        stay()
    }

    when(Operational) {
      case Event(RequestProduct(product), Initialized(inventory, prices)) =>
        inventory.get(product) match {
          case None | Some(0) =>
            sender() ! VendingError("ProductNotAvailable")
            stay() // !!! stay Operational
          case Some(_) =>
            val price: Int = prices(product)
            sender() ! Instruction(s"Please insert $price USD")
            goto(WaitForMoney) using WaitForMoneyData(inventory, prices, product, 0, sender())
//          equivalent with:  context.become(waitForMoney(inventory, prices, product, 0, startReceiveMoneyTimeoutSchedule, sender()))
        }
    }

    when(WaitForMoney, stateTimeout = 1 second) {
      case Event(StateTimeout, WaitForMoneyData(inventory, prices, product, money, requester)) =>
        requester ! VendingError("RequestTimeout")
        if (money > 0) requester ! GiveBackChange(money)
        goto(Operational) using (Initialized(inventory, prices))
//        context.become(operational(inventory, prices))
      case Event(ReceiveMoney(amount), WaitForMoneyData(inventory, prices, product, money, requester)) =>
//        moneyTimeoutSchedule.cancel()
        val price = prices(product)
        if (money + amount >= price) {
          // user buys product
          requester ! Deliver(product)
          // deliver the change
          if (money + amount - price > 0) requester ! GiveBackChange(money + amount - price)
          // updating inventory
          val newStock = inventory(product) - 1
          val newInventory = inventory + (product -> newStock)
          goto(Operational) using Initialized(newInventory, prices)
//        equivalent with:    context.become(operational(newInventory, prices))

        } else {
          val remainingMoney = price - money - amount
          requester ! Instruction(s"Please insert $remainingMoney USD")
          // replace goto(WaitForMoney) by stay() !!!
//          goto(WaitForMoney) using WaitForMoneyData(inventory, prices, product, money + amount, requester)
          stay() using WaitForMoneyData(inventory, prices, product, money + amount, requester)
//        equivalent with:
//          context.become(
//            waitForMoney(inventory, prices, product,
//              money + amount, // changed
//              // startReceiveMoneyTimeoutSchedule, requester
//            ))
        }
    }
    whenUnhandled {
      case Event(_, _) =>
        sender() ! VendingError("CommandNotFound")
        stay()
    }

    onTransition {
      case (stateA, stateB) => log.info(s"Transitioning from $stateA to $stateB")
    }

    initialize()

  }

}
