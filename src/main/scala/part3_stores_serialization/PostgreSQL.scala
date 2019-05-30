package part3_stores_serialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object PostgreSQL extends App {

  val system = ActorSystem("PostgreSQLDemo",ConfigFactory.load().getConfig("psqlStore"))

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
