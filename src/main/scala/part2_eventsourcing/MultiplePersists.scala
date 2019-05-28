package part2_eventsourcing

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object MultiplePersists extends App {
  val system = ActorSystem("MultiplePersist")

  /*
    Diligent accountant: with every invoice, will persist TWO events
      - a tax record for the fiscal authority
      - an invoice record for personal logs or some auditing authority


   */

  // COMMAND
  case class Invoice(recipient:String, date:Date, amount:Int)

  // EVENTS
  case class TaxRecord(taxId:String, recordId:Int, date:Date, totalAmount:Int)
  case class InvoiceRecord(invoiceRecordId:Int,recipient:String, date:Date, amount:Int)

  object DiligentAccountant {
    def props(taxId:String, taxAuthority:ActorRef) = Props(new DiligentAccountant(taxId,taxAuthority))
  }

  class DiligentAccountant(taxId:String, taxAuthority:ActorRef) extends PersistentActor with ActorLogging {

    var latestTaxRecordId = 0
    var latestInvoiceRecordId = 0

    override def receiveRecover: Receive = {
      case event => log.info(s"recovered: $event")
    }

    override def receiveCommand: Receive = {
      case Invoice(recipient,date,amount) =>
        /*
          1) create events
          2) persist events
          3) handle successful persist
         */

        val tr = TaxRecord(taxId,latestTaxRecordId, date, amount / 3)
        // journal ! TaxRecord
        persist(tr) {
          record =>
            taxAuthority ! record
            latestTaxRecordId += 1

            persist("I hereby declare this taxrecord to be true and complete.") {
              declaration =>
                taxAuthority ! declaration
            }
        }

        val ir = InvoiceRecord(latestInvoiceRecordId,recipient,date,amount)
        // journal ! InvoiceRecord
        persist(ir) {
          record =>
            taxAuthority ! record
            latestInvoiceRecordId += 1

            persist("I hereby declare this taxinvoice to be true and complete.") {
              declaration =>
                taxAuthority ! declaration
            }
        }
    }

    override def persistenceId: String = "diligent-accountant"
  }


  class TaxAuthority extends  Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"[tax authority] $message")
    }
  }

  val taxAuthority = system.actorOf(Props[TaxAuthority],"HMRC")
  val diligentAccountant = system.actorOf(DiligentAccountant.props("BR123-123",taxAuthority))

  diligentAccountant ! Invoice("The Sofa Company",new Date, 2000)
  /*
    The message ordering (TaxRecord -> InvoiceRecord) is GUARANTEED
   */

  /**
    * PERSISTENCE IS ALSO BASED ON MESSAGE PASSING
    */

  // nested persisting
  diligentAccountant ! Invoice("The Supercar Company",new Date, 13290)



}
