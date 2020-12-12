package part2_event_sourcing

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props

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
  mom ! MomStart(statelessKid)

}
