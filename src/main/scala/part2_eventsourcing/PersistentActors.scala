package part2_eventsourcing

import java.util.Date

import akka.actor.{ActorLogging, ActorSystem, PoisonPill, Props}
import akka.persistence.PersistentActor

object PersistentActors extends App {

  /*
    Scenario: we have a business and an accountant which keeps track of our invoices.
   */

  // COMMAND
  case class Invoice(recipient:String,date: Date, amount:Int)
  case class InvoiceBulk(invoices:List[Invoice])

  // Special messages
  case object Shutdown

  // EVENTS
  case class InvoiceRecorded(id:Int,recipient:String,date:Date,amount:Int)

  class Accountant extends PersistentActor with ActorLogging {

    var latestInvoiceId = 0
    var totalAmount = 0

    /**
      * Handler that will be called on recovery
      */
    override def receiveRecover: Receive = {
      /*
        best practice: follow the logic in the persist steps of receiveCommand
       */
      case InvoiceRecorded(id,_,_,amount) =>
        latestInvoiceId += 1
        totalAmount += amount
        log.info(s"Recovered invoice number # ${id} with amount $amount with total $totalAmount")
    }

    /**
      * The "normal" receive method
      */
    override def receiveCommand: Receive = {
      case InvoiceBulk(invoices) =>
        /*
          1) create events
          2) persist all the events
          3) update the actor state when each event is persisted
         */

      val invoiceIds = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events = invoices.zip(invoiceIds).map {pair =>
          val id = pair._2
          val invoice = pair._1
          InvoiceRecorded(id,invoice.recipient,invoice.date, invoice.amount)
        }
        persistAll(events) {
          e =>
            latestInvoiceId += 1
            totalAmount += e.amount
            log.info(s"Received single invoice for amount: $e")
        }
      case Invoice(recipient,date,amount) =>
        /*
        When you receive a comand
          1) you create an EVENT to persist into the store
          2) persist the event, then pass in a callback that will be triggered once the event is written
          3) we update the actor's state when the event has persisted
        */
        log.info(s"Received invoice for amount: $amount")
        persist(InvoiceRecorded(latestInvoiceId,recipient,date,amount))
          /* time gap: all other messages sent to this actor are stashed */
        {
          e =>
            // safe to access mutable state here
            // update state
            latestInvoiceId +=1
            totalAmount += amount

            // this is ok, correctly identifies the sender of the command
            // sender() ! "PersistenceACK"

            log.info(s"Persisted $e as invoice number # ${e.id}")
        }
        // act like normal actor
      case "print" =>
        log.info(s"latest invoice id: $latestInvoiceId, total amount: $totalAmount")


      case Shutdown => context.stop(self)

    }

    override def persistenceId: String = "simple-accountant" // best practice: make it unique

    /*
      This method is called if persisting failed.
      The actor will be STOPPED.

      Best practice: start the actor again after a while (use Backoff supervisor)
     */
    override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Fail to persist $event because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    /*
      Called if the JOURNAL fails to persist the event
      The actor is RESUMED.
     */
    override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist rejected for $event because of $cause")
      super.onPersistRejected(cause, event, seqNr)
    }
  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant],"simpleAccountant")

  for (i <- 1 to 10) {
    accountant ! Invoice("The Sofa Company",new Date,i*1000)
  }


  /*
    Persistence failures
   */

  /**
    * Persisting multiple events
    *
    * persistAll
    */

  val newInvoices = for (i <- 1 to 5) yield Invoice("The awesome chairs",new Date,i*2000)
  //  accountant ! InvoiceBulk(newInvoices.toList)

  /*
    NEVER EVER CALL PERSIST OR PERSISTALL FROM FUTURES.
   */

  /**
    * Shutdown of persistent actors.
    * Best practice: define your own "shutdown" messages
    */
//  accountant ! PoisonPill
  accountant ! Shutdown

}
