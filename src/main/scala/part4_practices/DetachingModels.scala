package part4_practices

import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.persistence.PersistentActor
import akka.persistence.journal.EventAdapter
import akka.persistence.journal.EventSeq
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object DetachingModels extends App {

  class CouponManager extends PersistentActor with ActorLogging {
    import DomainModel._

    val coupons: mutable.Map[String, User] = new mutable.HashMap[String, User]()

    override def persistenceId: String = "couponManager"

    override def receiveCommand: Receive = {
      case ApplyCoupon(coupon, user) =>
        if (!coupons.contains(coupon.code)) {
          persist(CouponApplied(coupon.code, user)) { e =>
            log.info(s"Persisted ${e}")
            coupons.put(coupon.code, user)
          }
        }
    }

    override def receiveRecover: Receive = {
      case event @ CouponApplied(code, user) =>
        log.info(s"Recovered $event")
        coupons.put(code, user)
    }
  }

  import DomainModel._
  val system = ActorSystem("DetachingModels", ConfigFactory.load().getConfig("detachingModels"))
  val couponManager = system.actorOf(Props[CouponManager], "couponManagerActor")

//  for (i <- 10 to 15) {
//    val coupon = Coupon(s"COUPON-$i", 100)
//    val user = User(s"$i", s"user_$i@rtjvm.com", s"John Doe $i")
//    couponManager ! ApplyCoupon(coupon, user)
//  }
}

// model
object DomainModel {

  case class User(id: String, email: String, name: String)

  case class Coupon(code: String, promotionAmount: Int)

  //command
  case class ApplyCoupon(coupon: Coupon, user: User)

  // event
  case class CouponApplied(code: String, user: User)
}

object DataModel {
  case class WrittenCouponApplied(code: String, userId: String, userEmail: String)
  case class WrittenCouponAppliedV2(code: String, userId: String, userEmail: String, userName: String)
}

class ModelAdapter extends EventAdapter {
  import DomainModel._
  import DataModel._

  override def manifest(event: Any): String = "CMA"

  // actor -> toJournal -> serializer -> journal
  override def toJournal(event: Any): Any = event match {
    case event @ CouponApplied(code, user) =>
      println(s"Converting $event to DATA model")
      WrittenCouponAppliedV2(code, user.id, user.email, user.name) // V2 !
  }

  // journal -> serializer -> fromJournal -> to the actor
  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case coupon @ WrittenCouponApplied(code, userId, userEmail) =>
      println(s"Converting $coupon to Domain model")
      val ca = CouponApplied(code, User(userId, userEmail, ""))
      EventSeq.single(ca)
    case coupon @ WrittenCouponAppliedV2(code, userId, userEmail, userName) =>
      println(s"Converting $coupon to Domain model V2")
      val ca = CouponApplied(code, User(userId, userEmail, userName))
      EventSeq.single(ca)

    case other =>
      EventSeq.single(other)
  }
}
