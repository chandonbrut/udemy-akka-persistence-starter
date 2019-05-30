package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventAdapter, EventSeq}
import com.typesafe.config.ConfigFactory
import part4_practices.DataModel.{WrittenCouponApplied, WrittenCouponAppliedV2}
import part4_practices.DomainModel.{ApplyCoupon, Coupon, CouponApplied, User}

import scala.collection.mutable

object DomainModel {
  case class User(id:String,email:String, name:String)
  case class Coupon(code:String,promotionAmount: Int)

  // command
  case class ApplyCoupon(coupon:Coupon, user:User)

  // event
  case class CouponApplied(code:String,user:User)
}

object DataModel {
  case class WrittenCouponApplied(code:String, userId:String, userEmail:String)
  case class WrittenCouponAppliedV2(code:String, userId:String, userEmail:String, userName:String)
}

class ModelAdapter extends EventAdapter {
  override def manifest(event: Any): String = "CMA"

  // actor -> toJournal -> serializer -> journal
  override def toJournal(event: Any): Any = event match {
    case event @ CouponApplied(code,user) =>
      println(s"converting $event to data")
      WrittenCouponAppliedV2(code,user.id,user.email,user.name)
  }

  // journal -> serializer -> fromJournal -> actor
  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case event @ WrittenCouponApplied(code,userId,userEmail) =>
      println(s"Converting from $event to domain")
      EventSeq.single(CouponApplied(code,User(userId,userEmail, "")))
    case event @ WrittenCouponAppliedV2(code,userId,userEmail,userName) =>
      println(s"Converting from $event to domain")
      EventSeq.single(CouponApplied(code,User(userId,userEmail,userName)))
    case other => EventSeq.single(other)
  }
}

object DetachingModels extends App {

  class CouponManager extends PersistentActor with ActorLogging {
    val coupons: mutable.Map[String,User] = new mutable.HashMap[String,User]()

    override def persistenceId: String = "coupon-manager"

    override def receiveCommand: Receive = {
      case ApplyCoupon(coupon,user) =>
        if (!coupons.contains(coupon.code)) {
          persist(CouponApplied(coupon.code,user)) {
            e =>
              log.info(s"persisted $e")
              coupons.put(coupon.code,user)
          }
        }
    }

    override def receiveRecover: Receive = {
      case event @ CouponApplied(code,user) =>
        log.info(s"recovered $event")
        coupons.put(code,user)
    }
  }

  val system = ActorSystem("DetachingModels", ConfigFactory.load().getConfig("detachingModels"))
  val couponManager = system.actorOf(Props[CouponManager],"couponManager")


//  for (i<- 5 to 10) {
//    val coupon =Coupon(s"MEGA_COUPON_$i",100)
//    val user =User(s"$i",s"user$i@rockthejvm.com",s"name $i")
//    couponManager ! ApplyCoupon(coupon,user)
//  }
}
