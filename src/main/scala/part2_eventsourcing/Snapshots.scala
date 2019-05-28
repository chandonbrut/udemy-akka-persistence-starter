package part2_eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

import scala.collection.mutable

object Snapshots extends App {

  val system = ActorSystem("SnapshotsDemo")

  // commands
  case class ReceivedMessage(contents:String) // FROM your contact
  case class SentMessage(contents:String) // TO your contact

  // events
  case class ReceivedMessageRecord(id:Int, contents:String)
  case class SentMessageRecord(id:Int, contents:String)


  object Chat {
    def props(owner:String, contact:String) = Props(new Chat(owner,contact))
  }

  class Chat(owner:String, contact:String) extends PersistentActor with ActorLogging {
    val MAX_MESSAGES = 10

    var commandsWithoutCheckpoint = 0
    var currentMessageId = 0
    val lastMessages = mutable.Queue[(String,String)]()

    override def receiveRecover: Receive = {
      case ReceivedMessageRecord(id,contents) =>
        log.info(s"Recovered received message:[$id][$contents]")
        maybeReplaceMessage(contact,contents)
        currentMessageId = id
      case SentMessageRecord(id,contents) =>
        log.info(s"Recovered sent message:[$id][$contents]")
        maybeReplaceMessage(owner,contents)
        currentMessageId = id
      case SnapshotOffer(metadata,contents) =>
        log.info(s"Recovered snapshot: $metadata")
        contents.asInstanceOf[mutable.Queue[(String,String)]].foreach(lastMessages.enqueue(_))
    }

    override def receiveCommand: Receive = {
      case ReceivedMessage(contents) =>
        persist(ReceivedMessageRecord(currentMessageId,contents)) {
          e =>
            log.info(s"Received message: $contents")
            maybeReplaceMessage(contact,contents)
            currentMessageId += 1
            maybeCheckpoint()
        }
      case SentMessage(contents) =>
        persist(SentMessageRecord(currentMessageId,contents)) {
          e =>
            log.info(s"Sent message: $contents")
            maybeReplaceMessage(owner,contents)
            currentMessageId += 1
            maybeCheckpoint()
        }

      // snapshot related stuff
      case SaveSnapshotSuccess(metadata) => log.info(s"save snapshot ok: $metadata")
      case SaveSnapshotFailure(metadata,reason) => log.warning(s"save snapshot $metadata failed because $reason")
    }

    def maybeReplaceMessage(sender:String,contents:String) = {
      if (lastMessages.size >= MAX_MESSAGES) {
        lastMessages.dequeue()
      }
      lastMessages.enqueue((sender,contents))
    }

    def maybeCheckpoint() = {
      commandsWithoutCheckpoint += 1
      if (commandsWithoutCheckpoint >= MAX_MESSAGES) {
        log.info("Saving checkpoint.")
        saveSnapshot(lastMessages) // async operation
        commandsWithoutCheckpoint = 0
      }
    }

    override def persistenceId: String = s"$owner-$contact-chat"
  }

  val chat = system.actorOf(Chat.props("daniel123","martin345"))

//  for (i <- 1 to 5000) {
//    chat ! ReceivedMessage(s"akka rocks $i")
//    chat ! SentMessage(s"akka rules $i")
//  }

  /*
  pattern:
    - after each persist, maybe save snapshot (logic is up to you)
    - if you save a snapshot, handle the SnapshotOffer in receiveRecover
    - optional: handle the SaveSnapshotSuccess and SaveSnapshotFailure in receiveCommand
    - profit from the extra speed
   */


}