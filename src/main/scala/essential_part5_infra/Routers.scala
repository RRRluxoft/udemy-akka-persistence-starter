package essential_part5_infra

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Terminated
import akka.routing.ActorRefRoutee
import akka.routing.Broadcast
import akka.routing.FromConfig
import akka.routing.RoundRobinGroup
import akka.routing.RoundRobinPool
import akka.routing.RoundRobinRoutingLogic
import akka.routing.Router
import com.typesafe.config.ConfigFactory

import scala.collection.immutable.IndexedSeq

object Routers extends App {

  /**
    * #1 - manual routed
    */
  case object Start
  case object Report

  class Master extends Actor {

    private val slaves: IndexedSeq[ActorRefRoutee] = for (i <- 1 to 5) yield {
      val slave = context.actorOf(Props[Slave], s"${i}_slave")
      context.watch(slave)
      ActorRefRoutee(slave)
    }

    private val router = Router(RoundRobinRoutingLogic(), slaves)

    override def receive: Receive = {
      case Terminated(ref) =>
        router.removeRoutee(ref)
        val newSlave = context.actorOf(Props[Slave])
        context.watch(newSlave)
        router.addRoutee(newSlave)
      case msg =>
        router.route(msg.toString, sender())
    }
  }

  class Slave extends Actor with ActorLogging {

    override def receive: Receive = {
      case msg => log.info(msg.toString)
    }
  }

  val system = ActorSystem("routersDemo")
  val master = system.actorOf(Props[Master], "masterActor")
//  for (i <- 1 to 10) {
//    master ! s"[$i] - hi there"
//  }

  /**
    * #2 - a router actor with its own child
    * POOL router
    */
  // 2.1 - programmatically (in code)
  val poolMaster = system.actorOf(RoundRobinPool(5).props(Props[Slave]), "simplePoolMaster")
//  for (i <- 1 to 10) {
//    poolMaster ! s"[$i] - hi there"
//  }

  // 2.2 - from config
  val system2 = ActorSystem("routersDemo2", ConfigFactory.load().getConfig("routersDemo"))
  val poolMaster2 = system2.actorOf(FromConfig.props(Props[Slave]), "poolMaster2")
//  for (i <- 1 to 10) {
//    poolMaster2 ! s"[$i] - hi there"
//  }

  /**
    * #3 - router with actors created elsewhere
    * GROUP router
    */
  val system3 = ActorSystem("routersDemo3", ConfigFactory.load().getConfig("routersDemo"))
  val slaveList = (1 to 5).map(i => system3.actorOf(Props[Slave], s"slave_$i")).toList
  val slavePaths = slaveList.map(ref => ref.path.toString)

  // 3.1 - in the code:
  val groupMaster = system3.actorOf(RoundRobinGroup(slavePaths).props())
//  for (i <- 1 to 10) {
//    groupMaster ! s"[$i] - hi there3"
//  }

  // 3.2 - from configuration
  val groupMaster2 = system3.actorOf(FromConfig.props(), "groupMaster2")
  for (i <- 1 to 10) {
    groupMaster2 ! s"[$i] - hi there from group"
  }

  /**
    * Special mesgs
    */
  groupMaster2 ! Broadcast("hello everyone")
}
