package part2_eventsourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistAsyncDemo extends App {

  val system = ActorSystem("PersistAsyncDemo")

  case class Command(contents:String)
  case class Event(contents:String)

  object CriticalStreamProcessor {
    def props(eventAggregator:ActorRef) = Props(new CriticalStreamProcessor(eventAggregator))
  }
  class CriticalStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case message => log.info(s"recovered $message")
    }

    override def receiveCommand: Receive = {
      case Command(contents) =>
        eventAggregator ! s"Processing $contents"
        persistAsync(Event(contents)) /*                                             TIME GAP                                 */ { e =>
            eventAggregator ! e
        }

        // some actual computation
        val processedContents = contents + "_processed"
        persistAsync(Event(processedContents)) /*                                    TIME GAP                                 */ { e =>
            eventAggregator ! e
        }

    }

    override def persistenceId: String = "critical-stream-processor"
  }

  class EventAggregator extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"crunching data ::: $message")
    }
  }

  val streamProcessor = system.actorOf(CriticalStreamProcessor.props(system.actorOf(Props[EventAggregator],"eventAgg")),"streamProc")

  streamProcessor ! Command("command1")
  streamProcessor ! Command("command2")

  /*
    persistAsync vs persist
      - performance: high-throughput environments
      - shouldn't use with mutable state (persistAsync doesn't enforce ordering)
   */


}
