package part3_stores_serialization

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import com.typesafe.config.ConfigFactory

object LocalStores extends App {

  val system = ActorSystem("LocalStoresDemo",ConfigFactory.load().getConfig("localStore"))




  val simpleActor = system.actorOf(Props[SimplePersistentActor],"simplePersistentActor")

  for (i <- 1 to 10) {
    simpleActor ! s"I love Akka[$i]"
  }

  simpleActor ! "print"

  simpleActor ! "snap"


  for (i <- 11 to 20) {
    simpleActor ! s"I love Akka[$i]"
  }



}
