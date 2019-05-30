package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventSeq, ReadEventAdapter}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object EventAdapters extends App {


  //store for acoustic guitar
  val ACOUSTIC = "acoustic"
  val ELECTRIC = "electric"

  // data structure
  case class Guitar(id:String,model:String,make:String, guitarType:String)

  //commands
  case class AddGuitar(guitar:Guitar, quantity:Int)

  //event
  case class GuitarAdded(guitarId:String, guitarModel:String, guitarMake:String, quantity:Int)
  case class GuitarAddedV2(guitarId:String, guitarModel:String, guitarMake:String, quantity:Int,  guitarType:String)



  class InventoryManager extends PersistentActor with ActorLogging {

    def addGuitarInventory(guitar:Guitar, quantity:Int) = {
      val existingQuantity = inventory.getOrElse(guitar,0)
      inventory.put(guitar,existingQuantity+quantity)
    }

    override def receiveRecover: Receive = {

      case event @ GuitarAddedV2(id,model,make,quantity,guitarType) =>
        log.info(s"recovered $event")
        val guitar = Guitar(id,model,make,guitarType)
        addGuitarInventory(guitar,quantity)
        log.info(s"Added $quantity x $guitar")

    }

    override def receiveCommand: Receive = {
      case AddGuitar(guitar @ Guitar(id,model,make,guitarType),quantity) =>
        persist(GuitarAddedV2(id,model,make,quantity,guitarType)) {
          _ =>
            addGuitarInventory(guitar,quantity)
            log.info(s"Added $quantity x $guitar")
        }
      case "print" => log.info(s"current inventory is $inventory")

    }


    override def persistenceId: String = "guitar-inventory-manager"

    val inventory: mutable.Map[Guitar,Int] = new mutable.HashMap[Guitar,Int]
  }

  class GuitarReadEventAdapter extends ReadEventAdapter {
/*
      journal -> serializer -> read event adapter -> actor
      (bytes) -> (GuitarAdded) -> (GuitarAddedV2) ->
*/
    override def fromJournal(event: Any, manifest: String): EventSeq = event match {
      case GuitarAdded(guitarId,guitarModel,guitarMake,guitarQuantity) =>
        EventSeq.single(GuitarAddedV2(guitarId,guitarModel,guitarMake,guitarQuantity,ACOUSTIC))
      case other => EventSeq.single(other)
    }
  }

  // WriteEventAdapter - used for backwards compatibility
  // actor -> write event adapter -> serializer -> journal
  // extends EventAdapter


  val system = ActorSystem("EventAdaptersDemo",ConfigFactory.load().getConfig("eventAdapters"))
  val invManager = system.actorOf(Props[InventoryManager],"inventoryManaer")

//  val guitars = for (i <- 1 to 10) yield Guitar(s"$i",s"Hakker $i",s"RockTheJVM", ACOUSTIC)
//  guitars.foreach(invManager ! AddGuitar(_,5))
  invManager ! "print"
}
