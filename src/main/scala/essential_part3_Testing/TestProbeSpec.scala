package essential_part3_Testing

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import akka.testkit.TestProbe
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike

class TestProbeSpec extends TestKit(ActorSystem("TestProbeSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {
  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  import TestProbeSpec._
  "A master actor" should {
    "register a slave" in {
      val master = system.actorOf(Props[Master], "masterActor")
      val slave = TestProbe("slaveActor")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
    }

    "send the work to the slave actor" in {
      val master = system.actorOf(Props[Master], "masterActor-2")
      val slave = TestProbe("slaveActor-2")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workLoadString = "I love Akka"
      master ! Work(workLoadString)

      slave.expectMsg(SlaveWork(workLoadString, testActor))
      slave.reply(WorkCompleted(3, testActor))

      expectMsg(Report(3))
    }

    "aggregate data correctly: send 2 pieces of work" in {
      val master = system.actorOf(Props[Master], "masterActor-3")
      val slave = TestProbe("slaveActor-3")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workLoadString = "I love Akka"
      master ! Work(workLoadString)
      master ! Work(workLoadString)

      slave.receiveWhile() {
        case SlaveWork(`workLoadString`, `testActor`) => slave.reply(WorkCompleted(3, testActor))
      }

      expectMsg(Report(3))
      expectMsg(Report(6))
    }
  }
}

object TestProbeSpec {
  case class Register(ref: ActorRef)
  case class Work(text: String)
  case class SlaveWork(text: String, originalRequester: ActorRef)
  case class WorkCompleted(count: Int, originalRequester: ActorRef)
  case class Report(wordCount: Int)
  case object RegistrationAck

  class Master extends Actor {

    override def receive: Receive = {
      case Register(slaveRef) =>
        sender() ! RegistrationAck
        context.become(onLine(slaveRef, 0))
      case _ => // ignore
    }

    def onLine(slaveRef: ActorRef, totalWordCount: Int): Receive = {
      case Work(text) => slaveRef ! SlaveWork(text, sender())
      case WorkCompleted(count, originalRequester) =>
        val newTotalWordCount = totalWordCount + count
        originalRequester ! Report(newTotalWordCount)
        context.become(onLine(slaveRef, newTotalWordCount))
    }
  }

}
