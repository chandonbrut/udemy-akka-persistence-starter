package part3_stores_serialization

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}


case class Command(contents:String)
case class Event(contents:String)

class SimplePersistentActor extends PersistentActor with ActorLogging {

    override def receiveRecover: Receive = {

      case SnapshotOffer(metadata,payload:Int) =>
        log.info(s"Recovered snapshot: $metadata")
        nMessages = payload

      case RecoveryCompleted => log.info("recovery completed.")

      case message =>
        log.info(s"recovered $message")
        nMessages+=1

    }

    override def receiveCommand: Receive = {
      case "print" => log.info(s"I have persisted $nMessages messages")

      case "snap" =>
        saveSnapshot(nMessages)

      case SaveSnapshotSuccess(metadata) => log.info(s"save snapshot was successful $metadata")

      case SaveSnapshotFailure(metadata,cause) => log.info(s"save snapshot failed because $cause")

      case message => persist(message) {
        e =>
          log.info(s"Persisting $message")
          nMessages += 1
      }


    }

    override def persistenceId: String = "simple-persistent-actor"

    // mutable state
    var nMessages = 0
  }