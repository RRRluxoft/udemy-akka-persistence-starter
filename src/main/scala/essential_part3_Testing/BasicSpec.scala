package essential_part3_Testing

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike

import scala.concurrent.duration.DurationInt
import scala.util.Random

class BasicSpec extends TestKit(ActorSystem("BasicSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  import BasicSpec._
  "A simple actor" should {
    "send back the same message" in {
      val echoActor = system.actorOf(Props[SimpleActor])
      val msg = s"Hello, test!"
      echoActor ! msg
      expectMsg(msg) // akka.test.single-expect-default
//      testActor !
    }
  }

  "A blackhole actor" should {
    "send back some message" in {
      val blackHoleActor = system.actorOf(Props[BlackHole])
      val msg = s"Hello, test!"
      blackHoleActor ! msg
      expectNoMessage(1 second) // akka.test.single-expect-default
    }
  }

  "A lab test actor" should {
    val labTestACtor = system.actorOf(Props[LabTestACtor], "labTestActor")
    val msg = s"This iS a meSsaGe"
    "return a string into uppercase" in {
      labTestACtor ! msg
      val reply = expectMsgType[String]
//      expectMsg(msg.toUpperCase)
      assert(reply == msg.toUpperCase)
    }
    "reply to a greeting" in {
      labTestACtor ! "greeting"
      expectMsgAnyOf("hi", "hello")
    }
    "reply with favorite tech" in {
      labTestACtor ! "favoriteTech"
      expectMsgAllOf("Scala", "Akka")
    }
    "reply with cool tech in a fancy way" in {
      labTestACtor ! "favoriteTech"
      expectMsgPF() {
        case "Akka"  => // care ONLY that PF is defined :~)
        case "Scala" =>
      }
    }
  }
}

object BasicSpec {

  class SimpleActor extends Actor {

    override def receive: Receive = {
      case msg => sender() ! msg
    }
  }

  class BlackHole extends Actor {
    override def receive: Receive = Actor.emptyBehavior
  }

  class LabTestACtor extends Actor {

    val random = new Random()

    override def receive: Receive = {
      case "greeting" =>
        if (random.nextBoolean()) sender() ! "hi"
        else sender() ! "hello"
      case "favoriteTech" =>
        sender() ! "Scala"
        sender() ! "Akka"
      case msg: String => sender() ! msg.toUpperCase
    }
  }
}
