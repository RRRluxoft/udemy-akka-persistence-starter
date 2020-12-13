package part2_event_sourcing

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import part3_essentials_exercise.Counter.CounterActor

object ChangingActorBehavior extends App {

  object FussyKid {
    case object KidAccept
    case object KidReject
    val HAPPY = "happy"
    val SAD = "sad"
  }

  class FussyKid extends Actor {
    import FussyKid._
    import Mom._
    var state = HAPPY

    override def receive: Receive = {
      case Food(VEGETABLE) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(_) =>
        if (state == HAPPY) sender() ! KidAccept
        else sender() ! KidReject
    }
  }

  // we wont use state !
  class StatelessKid extends Actor {
    import FussyKid._
    import Mom._

    override def receive: Receive = happyReceive

    def happyReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, discardOld = false) // change my receive handler
      case Food(CHOCOLATE) => // context.become(happyReceive, discardOld = false)
      case Ask(_)          => sender() ! KidAccept
    }

    def sadReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, discardOld = false)
      case Food(CHOCOLATE) => context.unbecome() // change my receive handler
      case Ask(_)          => sender() ! KidReject
    }
  }

  object Mom {
    case class MomStart(kidRef: ActorRef)
    case class Food(food: String)
    case class Ask(msg: String) // do you want to play?
    val VEGETABLE = "veggies"
    val CHOCOLATE = "chocolate"
  }

  class Mom extends Actor {
    import Mom._
    import FussyKid._

    override def receive: Receive = {
      case MomStart(kidRef) =>
        // test our interaction
        kidRef ! Food(VEGETABLE)
        kidRef ! Food(VEGETABLE)
        kidRef ! Food(CHOCOLATE)
//        kidRef ! Food(CHOCOLATE)
        kidRef ! Ask("do you want to play?")
      case KidAccept => println(s"Yeah, my kid is happy!")
      case KidReject => println(s"My kid is sad, but healthy!")
    }
  }

  val system = ActorSystem("ChangingActorBehavior")
  val kid = system.actorOf(Props[FussyKid], "childActor")
  val statelessKid = system.actorOf(Props[FussyKid], "statelessChildActor")
  val mom = system.actorOf(Props[Mom], "momActor")

  import Mom._
//  mom ! MomStart(kid)
//  mom ! MomStart(statelessKid) // the same behavior
  /*
  Food(veg) -> stack.push(sadReceive)
  Food(chocca) -> stack.push(happyReceive) //become.happyReceive

  Stack:
  1. happyReceive
  2. sadReceive
  3. happyReceive
   */

  /*
  new behavior:
  Food(veg) -> stack.push(sadReceive) 1. happy --> 1.sad 2.happy
  Food(veg) -> stack.push(sadReceive) 1.sad 2. happy --> 1.sad 2.sad 3.happy
  Food(choco) -> stack.push(happyReceive) //become.happyReceive  - replaced by unbecome : 1.sad 2.sad 3.happy --> 1.sad 2.happy
  Food(choco) -> stack.push(happyReceive) 1.sad 2.happy --> 1.happy


  Stack:
  1. sadReceive
  2. sadReceive
  3. lessSad
   */
//  mom ! MomStart(statelessKid)

  /*
  Exercises:
  1- create the Counter
   */
  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }

  class Counter extends Actor {
    import Counter._
    override def receive: Receive = countReceive(0)

    def countReceive(in: Int): Receive = {
      case Increment =>
        println(s"[$in] incrementing")
        context.become(countReceive(in + 1), false)
      case Decrement =>
        println(s"[$in] decrementing")
//        context.become(countReceive(in - 1))
        context.unbecome()
      case Print => println(s"[counter] current count is $in")
    }
  }

  val countSystem = ActorSystem("countSystem")
  val counterActor = countSystem.actorOf(Props[Counter], "counter")

  import Counter._
  (1 to 5).foreach(_ => counterActor ! Increment)
  (1 to 3).foreach(_ => counterActor ! Decrement)
  counterActor ! Print

  /*
  Ex2 - a simplified voiting system
   */
  case class Vote(candidate: String)
  case object VoteStatusRequest
  case class VoteStatusReply(candidate: Option[String])

  class Citizen extends Actor {

//    var candidate: Option[String] = None
    override def receive: Receive = {
      case Vote(c)           => context.become(voted(c)) //this.candidate = Some(c)
      case VoteStatusRequest => sender() ! VoteStatusReply(None)
    }

    def voted(candidate: String): Receive = {
      case VoteStatusRequest => sender() ! VoteStatusReply(Some(candidate))
    }
  }

  case class AggregateVotes(citizens: Set[ActorRef])

  class VoteAggregator extends Actor {

//    var stillWaiting: Set[ActorRef] = Set()
//    var currentStats: Map[String, Int] = Map()

    override def receive: Receive = awaitingCommand

    def awaitingCommand: Receive = {
      case AggregateVotes(citizens) =>
        citizens.foreach(citizensRef => citizensRef ! VoteStatusRequest)
        context.become(awaitingStatuses(citizens, Map()))
    }

    def awaitingStatuses(stillWaiting: Set[ActorRef], currentStats: Map[String, Int]): Receive = {
      case VoteStatusReply(maybeCandidate) =>
        maybeCandidate match {
          case Some(c) =>
            val newStillWaiting = stillWaiting - sender()
            val currentVotesCandidate = currentStats.getOrElse(c, 0)
            val newStats = currentStats + (c -> (currentVotesCandidate + 1))
            if (newStillWaiting.isEmpty) {
              println(s"[aggregator] poll stats: $newStats")
            } else {
              // still need to process some statuses
              //                  stillWaiting = newStillWaiting
              context.become(awaitingStatuses(newStillWaiting, newStats))
            }
          case None => sender() ! VoteStatusRequest
        }
    }
  }

  val alice = system.actorOf(Props[Citizen])
  val bob = system.actorOf(Props[Citizen])
  val charlie = system.actorOf(Props[Citizen])
  val daniel = system.actorOf(Props[Citizen])

  alice ! Vote("Martin")
  bob ! Vote("Jonas")
  charlie ! Vote("Roland")
  daniel ! Vote("Martin")

  val voteAggregator = system.actorOf(Props[VoteAggregator])
  voteAggregator ! AggregateVotes(Set(alice, bob, charlie, daniel))

}
