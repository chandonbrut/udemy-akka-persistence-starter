package part2_eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

import scala.collection.mutable

object PersistentActorsExercise extends App {
  /*
    Persistent actor for a voting station
    Keep:
      - the citizens who voted
      - the poll: mapping between a candidate and the number of received votes so far

    The actor must be able to recover its state if it's shutdown or restarted
   */

  case class Vote(citizenPID:String,candidate:String)
  // case class VoteRecorded(citizenPID:String,candidate:String)

  class VotingStation extends PersistentActor with ActorLogging {

    // ignore the mutable state for now
    val citizens = new mutable.HashSet[String]()
    val poll = new mutable.HashMap[String,Int]()

    override def receiveRecover: Receive = {
      case vote @ Vote(citizenPID,candidate) =>
        log.info(s"Recovered $vote")
        handleInternalStateChange(citizenPID,candidate)
    }

    override def receiveCommand: Receive = {

      case vote @ Vote(citizenPID,candidate) =>
        if (!citizens.contains(vote.citizenPID)) {

          /*
          1) create event
          2) persist event
          3) handle state change after persisting is successful
         */
          persist(vote) { // COMMAND sourcing
            _ =>
              log.info(s"Persisted $vote")
              handleInternalStateChange(citizenPID,candidate)
          }
        } else {
          log.warning(s"Citizen $citizenPID is trying to vote again!!!")
        }
      case "print" => log.info(s"current state:\n\tcitizens: $citizens\n\tpoll: $poll")
    }

    def handleInternalStateChange(citizenPID:String,candidate:String) = {
      citizens.add(citizenPID)
      val votes = poll.getOrElse(candidate,0)
      poll.put(candidate,votes+1)

    }

    override def persistenceId: String = "simple-voting-station"
  }


  val system = ActorSystem("PersistentActorExercise")
  val votingStation = system.actorOf(Props[VotingStation],"simpleVotingStation")

  val votesMap = Map[String,String](
    "Alice" -> "Martin",
    "Bob" -> "Roland",
    "Charlie" -> "Martin",
    "David" -> "Jonas",
    "Daniel" -> "Martin"
  )

//  votesMap.keys.foreach(citizen =>
//    votingStation ! Vote(citizen,votesMap(citizen))
//  )

  votingStation ! Vote("Daniel","Daniel")
  votingStation ! "print"

}
