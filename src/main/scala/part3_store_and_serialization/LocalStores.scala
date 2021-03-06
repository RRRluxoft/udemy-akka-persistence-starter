package part3_store_and_serialization

import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory

object LocalStores extends App {

  val localStoreActorSystem = ActorSystem("localStoreActorSystem", ConfigFactory.load().getConfig("localStores"))
  val persistentActor = localStoreActorSystem.actorOf(Props[SimplePersistentActor], "persistentActor")

  for (i <- 1 to 10) {
    persistentActor ! s"I love Akka [${i}]"
  }

  persistentActor ! "print"
  persistentActor ! "snap"

  for (i <- 11 to 20) {
    persistentActor ! s"I love Akka [${i}]"
  }

}
