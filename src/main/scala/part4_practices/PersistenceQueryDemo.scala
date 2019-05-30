package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{EventAdapter, EventSeq, Tagged, WriteEventAdapter}
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.util.Random

object PersistenceQueryDemo extends App {

  val system = ActorSystem("PersistenceQueryDemo", ConfigFactory.load().getConfig("persistenceQueryDemo"))

  // read journal
  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  // gives me all persistence IDs
  val persistenceIds = readJournal.persistenceIds()

  // boilerplate so far
  implicit val materializer = ActorMaterializer()(system)
//  persistenceIds.runForeach { persistenceId =>
//    println(s"Found persistenceID: $persistenceId")
//  }

  class SimplePersistentActor extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case e => log.info(s"recovered: $e")
    }

    override def receiveCommand: Receive = {
      case m => persist(m) { _ => log.info(s"persisted $m") }
    }

    override def persistenceId: String = "persistence-query-id-1"
  }

  val simpleActor = system.actorOf(Props[SimplePersistentActor],"simplePersistentActor")

  import scala.concurrent.duration._


  import system.dispatcher
  system.scheduler.scheduleOnce(5 seconds) {
    simpleActor ! "persistThis"
    simpleActor ! "persistThat"
  }


  // events by persistence ID
  val events = readJournal.eventsByPersistenceId("persistence-query-id-1",0,Long.MaxValue)

  events.runForeach {
    event =>
      println(s"read event: $event")
  }

  // events by tags
  val genres = Array("pop", "rock", "hip-hop", "jazz", "disco")
  case class Song(artist:String,title:String,genre:String)
  case class Playlist(songs:List[Song])
  case class PlaylistPurchased(id:Int, songs:List[Song])

  class MusicStoreCheckoutActor extends PersistentActor with ActorLogging {
    var latestId = 0

    override def receiveRecover: Receive = {
      case e @ PlaylistPurchased(id,songs) =>
        latestId = id
        log.info(s"recovered: $e")
    }

    override def receiveCommand: Receive = {
      case Playlist(songs) =>
        persist(PlaylistPurchased(latestId,songs)) {
          e => log.info(s"user bought $songs")
            latestId+=1
        }

    }

    override def persistenceId: String = "music-store-checkout"
  }

  class MusicStoreEventAdapter extends WriteEventAdapter {
    override def toJournal(event: Any): Any = event match {
      case event@PlaylistPurchased(id, songs) =>
        val genres = songs.map(_.genre).toSet
        Tagged(event, genres)
      case event => event
      }
    override def manifest(event: Any): String = ""
    }




  val checkoutActor = system.actorOf(Props[MusicStoreCheckoutActor], "musicStoreActor")

  val r = new Random()
  for (_ <- 1 to 10) {

    val maxSongs = r.nextInt(5)
    val songs = for (i <- 1 to maxSongs) yield {
      val randomGenre = genres(r.nextInt(5))
      Song(s"Artist $i", s"Song $i", randomGenre)
    }

    checkoutActor ! Playlist(songs.toList)
  }

  val rockPlaylists = readJournal.eventsByTag("rock", Offset.noOffset)
  rockPlaylists.runForeach {
    e => println(s"Found a playlist with a rock song: $e")
  }

}
