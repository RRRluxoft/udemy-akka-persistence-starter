package essential_part5_infra

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random

object Dispatchers extends App {

  class Counter extends Actor with ActorLogging {
    var count = 0

    override def receive: Receive = {
      case msg =>
        count += 1
        log.info(s"[$count] $msg")
    }
  }

  val system = ActorSystem("DispatchersDemo" /*, ConfigFactory.load().getConfig("dispatchersDemo")*/ )

  // #1 - programmatically
  val actors = for (i <- 1 to 10) yield system.actorOf(Props[Counter].withDispatcher("my-dispatcher"), s"counter_$i")
  val r = new Random()
//  for (i <- 1 to 1000) {
//    actors(r.nextInt(10)) ! i
//  }

  // #2 - from config
  val rtjvmActor = system.actorOf(Props[Counter], "rtjmv")

  /**
    * Dispatchers implement theExecutionContext trait
    */

  class DBActor extends Actor with ActorLogging {
    // solution #1
    implicit val ec: ExecutionContext = context.system.dispatchers.lookup("my-dispatcher") // context.dispatcher - still blocking
    // solution #2 - use Router
    override def receive: Receive = {
      case msg =>
        Future {
          // wait for resources
          Thread.sleep(2000)
          log.info(s"Success: $msg")
        }
    }
  }

  val dbActor = system.actorOf(Props[DBActor])
//  dbActor ! "the reason is ..." // - blocking !!!

  val nonBlockingActor = system.actorOf(Props[Counter])
  for (i <- 1 to 1000) {
    val msg = s"important msg: $i"
    dbActor ! msg
    nonBlockingActor ! msg
  }
}
