package essential_part6_patterns

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Stash

import scala.sys.Prop

object StashDemo extends App {

  /**
    * ResourcesActor
    * - open -> it can receive read/write requests to the resource
    * - otherwise it will postpone all read/write requests until the state is open
    *
    * ResourcesActor is closed
    * - Open => switch to open state
    * - Read, Write msgs are POSTPONED
    *
    * ResourcesActor is open
    * - Read, Write msgs are handled
    * - Close => switch to the closed state
    *
    * [Read, Open, Write]
    * - stash Read
    * Stash: [Read]
    * - open => switch to open state
    * Mailbox: [Read, Write] - handled
    */
  case object Open
  case object Close
  case object Read
  case class Write(data: String)

  // step 1 - mix-in the Stash trait
  class ResourcesActor extends Actor with ActorLogging with Stash {
    private var innerData = ""

    override def receive: Receive = closed

    def closed: Receive = {
      case Open =>
        log.info("Opening resource")
        // step 3- unstashAll msgs
        unstashAll()
        context.become(open)
      case msg =>
        log.info(s"Stashing $msg cause I cant handle it in the closed state")
        // step 2 - stash away what you cant handle
        stash()
    }

    def open: Receive = {
      case Read =>
        // do
        log.info(s"I've Read $innerData")
      case Write(data) =>
        log.info(s"Im writing $data")
        innerData = data
      case Close =>
        log.info("Closing resources")
        unstashAll()
        context.become(closed)
      case msg =>
        log.info(s"Stashing $msg cause I cant handle it in the open state")
        stash()
    }
  }

  val system = ActorSystem("StashDemo")
  val resourceActor = system.actorOf(Props[ResourcesActor], "resourceActor")

  resourceActor ! Read // stashed
  resourceActor ! Open // opening and unstash : 'I've Read ""'
  resourceActor ! Open // cant handle it and stash
  resourceActor ! Write("I got stash") // write
  resourceActor ! Close // closing and unstashAll; and Open again cause receive Open ??? !!!
  resourceActor ! Read // cant handle and stash - wrong : already open and 'I've Read ... msg.'

}
