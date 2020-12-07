package part2_event_sourcing

import java.util.Date

import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.persistence.PersistentActor

import scala.collection.mutable

object PersistentActorsExercise extends App {
  /*
  Persistent Actor for a voting station
  Keep:
    - the citizens who voted
    - the poll: mapping between a candidate and the number of received votes so far

  The actor must be able to recover its state if it's shutdown or restarted
   */

  case class Vote(citizenPID: String, candidate: String)

  sealed trait Candidate
  case object Candidate_Bob extends Candidate
  case object Candidate_Poul extends Candidate

  type Sum = (Candidate, Int)

  case class VoteRecorded(id: Int, citizenPID: String, candidate: String)

  class Calculator extends PersistentActor with ActorLogging {
    /*
    var latestVoteId = 0
    var totalVotesBob = 0
    var totalVotesPoul = 0
     */

    val citizens: mutable.Set[String] = new mutable.HashSet[String]()
    val poll: mutable.Map[String, Int] = new mutable.HashMap[String, Int]()

    override def persistenceId: String = "calc-actor"

    override def receiveCommand: Receive = {
      case vote_ @ Vote(citizenPID, candidate) =>
        if (!citizens.contains(vote_.citizenPID)) {
          log.info(s"Receive vote of citizen #$citizenPID , ok?")
          persist(vote_) { _ => // COMMAND sourcing
            log.info(s"Persisted : $vote_")
            handleInternalState(citizenPID, candidate)
          }
        } else {
          log.warning(s"Citizen $citizenPID is trying to vote multiple times!")
        }

      /*
        val vote = VoteRecorded(latestVoteId, citizenPID, candidate)
        persist(vote) { v =>
          latestVoteId += 1
          candidate match {
            case "Bob"  => totalVotesBob += 1
            case "Poul" => totalVotesPoul += 1
          }
          log.info(s"Voted #${v.citizenPID}. Total : ${totalVotesBob + totalVotesPoul}")
        }
       */

      case "print" =>
        log.info(s"Current state: \nCitizens: $citizens\npolls: $poll")
    }

    def handleInternalState(citizenPID: String, candidate: String): Option[Int] = {
      citizens.add(citizenPID)
      val votes = poll.getOrElse(candidate, 0)
      poll.put(candidate, votes + 1)
    }

    override def receiveRecover: Receive = {
//      case VoteRecorded(id, _, _) =>
      case vote @ Vote(citizenPID, candidate) =>
        log.info(s"Recovered: $vote")
        handleInternalState(citizenPID, candidate)
    }

  }

  val system = ActorSystem("vote-actor")
  val calculator = system.actorOf(Props[Calculator], "SimpleCalculator")

  val votesMap = Map[String, String](
    "Alice" -> "Martin",
    "Bob" -> "Roland",
    "Charlie" -> "Martin",
    "David" -> "Jonas",
    "Roman" -> "Martin"
  )

//  votesMap.keys.foreach(citizen => calculator ! Vote(citizen, votesMap.apply(citizen)))

  calculator ! Vote("Roman", "Roman")
  calculator ! "print"

}
