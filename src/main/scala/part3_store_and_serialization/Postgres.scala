package part3_store_and_serialization

import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory

object Postgres extends App {
  val postgresStoreActorSystem = ActorSystem("postgresStoreActorSystem", ConfigFactory.load().getConfig("postgresDemo"))
  val persistentActor = postgresStoreActorSystem.actorOf(Props[SimplePersistentActor], "persistentActor")

  for (i <- 1 to 10) {
    persistentActor ! s"I love Akka [${i}]"
  }

  persistentActor ! "print"
  persistentActor ! "snap"

  for (i <- 11 to 20) {
    persistentActor ! s"I love Akka [${i}]"
  }

}
