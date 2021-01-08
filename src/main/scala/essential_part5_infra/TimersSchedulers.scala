package essential_part5_infra

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.Timers

import scala.concurrent.duration._

object TimersSchedulers extends App {

  class SimpleActor extends Actor with ActorLogging {

    override def receive: Receive = {
      case msg => log.info(msg.toString)
    }
  }

  val system = ActorSystem("SchedulersTimerDemo")
//  val simpleActor = system.actorOf(Props[SimpleActor], "simpleACtor")
  implicit val ec = system.dispatcher
  // OR:
//  import system.dispatcher

  system.log.info("Scheduling reminder for simpleActor")
  /*
  system.scheduler.scheduleOnce(1 second) {
    simpleActor ! "reminder"
  }

  val routine: Cancellable = system.scheduler.schedule(1 second, 2 seconds) {
    simpleActor ! "heartbeat"
  }

  system.scheduler.scheduleOnce(5 seconds) {
    routine.cancel()
  }
   */

  /**
    * Exercise: implement a self-closing actor
    *
    * - if the actor receive a msg (anything), you have 1 second to send it another msg
    * - if the time window expires, the actor will stop itself
    * - if you send another msg, the time window is reset
    */

  class SelfClosingActor extends Actor with ActorLogging {
    var scheduler = createTimeoutWindow()

    def createTimeoutWindow(): Cancellable =
      context.system.scheduler.scheduleOnce(1 second) {
        self ! "timeout"
      }

    override def receive: Receive = {
      case "timeout" =>
        log.info("Stopping myself")
//        self ! PoisonPill
        context.stop(self)
      case msg =>
        log.info(s"Received $msg, staying alive")
        // reset time window
        scheduler.cancel()
        scheduler = createTimeoutWindow()
    }
  }

  /*
  val selfClosingActor = system.actorOf(Props[SelfClosingActor], "selfClosingActor")
  system.scheduler.scheduleOnce(250 millis)(selfClosingActor ! "ping")
  system.scheduler.scheduleOnce(2 seconds) {
    system.log.info(s"sending pong to the self-closing actor")
    selfClosingActor ! "pong"
  }
   */

  /**
    * Timer
    */
  case object TimerKey
  case object Start
  case object Reminder
  case object Stop

  class TimerBasedHeartbeatActor extends Actor with ActorLogging with Timers {
    timers.startPeriodicTimer(TimerKey, Start, 500 millis)

    override def receive: Receive = {
      case Start =>
        log.info("Bootstrapping")
        timers.startPeriodicTimer(TimerKey, Reminder, 1 second)
      case Reminder =>
        log.info(s"I am alive")
      case Stop =>
        log.warning(s"Stopping")
        timers.cancel(TimerKey)
//        self ! PoisonPill
        context.stop(self)
    }
  }

  val timerBasedHeartbeatActor = system.actorOf(Props[TimerBasedHeartbeatActor], "timerActor")
  system.scheduler.scheduleOnce(6 seconds) {
    timerBasedHeartbeatActor ! Stop
  }

}
