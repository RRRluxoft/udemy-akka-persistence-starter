package part4_practices

import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.Tagged
import akka.persistence.journal.WriteEventAdapter
import akka.persistence.query.Offset
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.Random

object PersistenceQueryDemo extends App {

  val system = ActorSystem("PersistenceQueryDemo", ConfigFactory.load().getConfig("persistenceQuery"))

  // read journal
  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  // give me ALL persistence IDs
  val persistenceIds = readJournal.currentPersistenceIds()

  implicit val materializer = ActorMaterializer()(system)
  persistenceIds.runForeach(persistenceId => println(s"Found persistence ID: $persistenceId"))

  class SimplePersistentActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "persistence-query-id-1"

    override def receiveCommand: Receive = {
      case m => persist(m)(_ => log.info(s"Persisted ${m}"))
    }

    override def receiveRecover: Receive = {
      case e => log.info(s"Recovered ${e}")
    }
  }

  val simpleActor = system.actorOf(Props[SimplePersistentActor], "simplePersistentActor")

//  import system.dispatcher
//  system.scheduler.scheduleOnce(5 seconds) {
//    for (i <- 1 to 1) yield simpleActor ! s"new Message-$i"
//  }

  // events by persistence id
  val events = readJournal.eventsByPersistenceId("persistence-query-id-1", 0, Long.MaxValue)
  events.runForeach(event => println(s"Read event: $event"))

  // events by Tags
  val genres = Array("pop", "rock", "hip-hop", "jazz", "funk")
  case class Song(artist: String, title: String, genre: String)
  //command
  case class Playlist(songs: List[Song])
  // event
  case class PlaylistPurchased(id: Int, songs: List[Song])

  class MusicStoreCheckoutActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "music-store-checkout"

    var latestPersistedId = 0

    override def receiveCommand: Receive = {
      case Playlist(songs) =>
        persist(PlaylistPurchased(latestPersistedId, songs)) { _ =>
          log.info(s"User purchased $songs")
          latestPersistedId += 1
        }
    }

    override def receiveRecover: Receive = {
      case event @ PlaylistPurchased(id, _) =>
        log.info(s"Recovered $event")
        latestPersistedId = id
    }
  }

  class MusicStoreEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = "MusicStore"

    override def toJournal(event: Any): Any = event match {
      case event @ PlaylistPurchased(_, songs) =>
        val genres = songs.map(_.genre).toSet
        Tagged(event, genres)
      case event => event
    }
  }

  val checkoutActor = system.actorOf(Props[MusicStoreCheckoutActor], "musicStoreActor")

  val r = new Random
  for (_ <- 1 to 10) {
    val maxSongs = r.nextInt(5)
    val songs = for (i <- 1 to maxSongs) yield {
      val randomGenre = genres(r.nextInt(5))
      Song(s"Artist $i", s"My love song $i", randomGenre)
    }

    checkoutActor ! Playlist(songs.toList)
  }

  val rockplaylist = readJournal.eventsByTag("rock", Offset.noOffset)
  rockplaylist.runForeach(event => println(s"Found a playlist with a rock song: $event"))
}
