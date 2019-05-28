package part2_eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}

object RecoveryDemo extends App {

  case class Command(contents:String)
  case class Event(id:Int,contents:String)

  class RecoveryActor extends PersistentActor with ActorLogging {

    override def receiveRecover: Receive = {
      case Event(id,contents) =>
        // if (contents.contains("314")) throw new RuntimeException("I can't take this number!")
        log.info(s"recovered $contents, recovery is ${if (this.recoveryFinished) "" else "NOT" } finished.")
        context.become(online(id+1))

      /*
          THIS WILL NOT CHANGE THE EVENT HANDLER DURING RECOVERY
          After recovery, the normal handler will be the result of ALL the stacking of context.becomes.
      */

      case RecoveryCompleted =>
        // additional initialization
        log.info("I have finished recovering")
    }

    override def receiveCommand: Receive = online(0)

    def online(eventId:Int): Receive = {
      case Command(contents) =>
        persist(Event(eventId,contents)) {
          e =>
            log.info(s"persisted $contents, recovery is ${if (this.recoveryFinished) "" else "NOT" } finished.")
            context.become(online(eventId+1))
        }
    }

    override def persistenceId: String = "recovery-actor"

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error("I failed at recovery!")
      super.onRecoveryFailure(cause, event)
    }

    // override def recovery:Recovery = Recovery(toSequenceNr = 100)
    // override def recovery:Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
//    override def recovery: Recovery = Recovery.none
  }

  val system = ActorSystem("RecoveryDemo")
  val actor = system.actorOf(Props[RecoveryActor])

  for (i <- 1 to 1000) {
    actor ! Command(s"yeah #$i")
  }

  /*
    Stashing commands
   */

  // ALL COMMANDS SENT DURING RECOVERY ARE STASHED

  /*
     2 - failure during recovery
          - onRecoveryFailure is called then the actor is stopped
   */


  /*
    3 - customize recovery
      - DO NOT PERSIST MORE EVENTS AFTER A CUSTOMIZED ***INCOMPLETE*** RECOVERY
   */

  /*
    4 - recovery status or KNOWING when you are done recovering.
      - getting a signal when you're done recovering!
   */

  /*
    5 - stateless actors
   */

}
