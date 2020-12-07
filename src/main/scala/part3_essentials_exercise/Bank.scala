package part3_essentials_exercise

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import part3_essentials_exercise.Bank.Person.LiveTheLife

object Bank extends App {

  // DOMAIN
  object BankAccount {
    sealed trait Action
    case class Deposit(amount: BigDecimal) extends Action
    case class Withdraw(amount: BigDecimal) extends Action
    case object Statement extends Action

    case class TransactionSuccess(msg: String)
    case class TransactionFailure(reason: String)
  }

  class BankAccount extends Actor {
    import BankAccount._
    var funds = BigDecimal("0")

    override def receive: Receive = {
      case Deposit(amount) =>
        if (amount <= 0) sender() ! TransactionFailure(s"[account] Pls add more then zero!")
        else {
          funds += amount
          sender() ! TransactionSuccess(s"You have a luck. Now $funds 'USD'")
        }
      case Withdraw(amount) if (amount > funds) => sender() ! TransactionFailure(s"Not enough money! Available $funds USD")
      case Withdraw(amount) =>
        funds -= amount
        sender() ! TransactionSuccess(s"So cool . Now ${funds} USD")
      case Statement => sender() ! (s"You have an $funds USD")
    }
  }

  // DOMAIN
  object Person {
    case class LiveTheLife(ref: ActorRef)
  }

  class Person extends Actor {
    import BankAccount._
    import Person._

    override def receive: Receive = {
      case LiveTheLife(account) =>
        account ! Deposit(BigDecimal("0"))
        account ! Deposit(BigDecimal("10000"))
        account ! Withdraw(BigDecimal("90000"))
        account ! Withdraw(BigDecimal("2175"))
        account ! Statement
      case msg => println(s"[person] $msg")
    }
  }

  val system = ActorSystem("bankSystem")
  val bankAccount = system.actorOf(Props[BankAccount], "account_1")
  val person = system.actorOf(Props[Person], "person")

  import BankAccount._
  person ! LiveTheLife(bankAccount)

}
