package essential_part4_fault_tolerance

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.AllForOneStrategy
import akka.actor.OneForOneStrategy
import akka.actor.Props
import akka.actor.SupervisorStrategy
import akka.actor.Terminated
import akka.actor.SupervisorStrategy.Escalate
import akka.actor.SupervisorStrategy.Restart
import akka.actor.SupervisorStrategy.Resume
import akka.actor.SupervisorStrategy.Stop
import akka.testkit.EventFilter
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike

class SupervisionSpec extends TestKit(ActorSystem("SupervisionSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {
  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  import SupervisionSpec._

  "A supervisor" should {
    "resume its child in case of minor fault" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]

      child ! "I love Akka"
      child ! Report
      expectMsg(3)

      child ! "Akka is awesome because I am learning to think ina whole new way"
      child ! Report
      expectMsg(3)
    }

    "restart its child in case of an empty sentence " in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]

      child ! "I love Akka"
      child ! Report
      expectMsg(3)

      child ! ""
      child ! Report
      expectMsg(0)
    }

    "terminate its child " in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]

      watch(child)

      child ! "akk is  nice"
      child ! Report
      val terminatedMsg = expectMsgType[Terminated]
      assert(terminatedMsg.actor == child)
    }

    "escalate an error when doesnt know what to do" in {
      val supervisor = system.actorOf(Props[Supervisor], "supervisor")
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]

      watch(child)

      child ! 43
      val terminatedMsg = expectMsgType[Terminated]
      assert(terminatedMsg.actor == child)
    }
  }

  "An all-for-one supervisor" should {
    "apply the all-for-one strategy" in {
      val supervisor = system.actorOf(Props[AllForOneSupervisor], "AllForOneSupervisor")
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]

      supervisor ! Props[WordCounter]
      val child2 = expectMsgType[ActorRef]

      child2 ! "Testing supervisor"
      child2 ! Report
      expectMsg(2)

      EventFilter[NullPointerException]() intercept {
        child ! ""
      }

      Thread.sleep(500)

      child2 ! Report
      expectMsg(0)
    }
  }
}

object SupervisionSpec {

  class Supervisor extends Actor {

    override val supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: NullPointerException     => Restart
      case _: IllegalArgumentException => Stop
      case _: RuntimeException         => Resume
      case _: Exception                => Escalate
    }

    override def receive: Receive = {
      case props: Props =>
        val childRef = context.actorOf(props)
        sender() ! childRef
    }
  }

  class AllForOneSupervisor extends Supervisor {

    override val supervisorStrategy: SupervisorStrategy = AllForOneStrategy() {
      case _: NullPointerException     => Restart
      case _: IllegalArgumentException => Stop
      case _: RuntimeException         => Resume
      case _: Exception                => Escalate
    }
  }

  case object Report

  class WordCounter extends Actor {
    var words = 0

    override def receive: Receive = {
      case Report => sender() ! words
      case ""     => throw new NullPointerException(s"sentence is empty")
      case sentence: String =>
        if (sentence.length > 20) throw new RuntimeException(s"sentence is too big")
        else if (!Character.isUpperCase(sentence(0)))
          throw new IllegalArgumentException("sentence must start with uppercase")
        else words += sentence.split(" ").length
      case _ => throw new Exception("can only receive strings")
    }
  }

}
