package part1_recap

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, PoisonPill, Props, Stash, SupervisorStrategy}
import akka.util.Timeout

object AkkaRecap extends App {

  class SimpleActor extends Actor with ActorLogging with Stash {
    override def receive: Receive = {
      case "createChild" =>
        val childActor = context.actorOf(Props[SimpleActor],"child")
        childActor ! "Hello"

      case "stashThis" =>
        stash()

      case "change handler NOW" =>
        unstashAll()
        context.become(anotherHandler)

      case "change" => context.become(anotherHandler)
      case message => println(s"I received: $message")
    }

    def anotherHandler:Receive = {
      case message => println(s"In another receive handler: $message")
    }

    override def preStart(): Unit = log.info("I'm starting")

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _:RuntimeException => Restart
      case _ => Stop
    }


  }

  //actor encapsulation
  val system = ActorSystem("AkkaRecap")

  // #1: you can only instantiate an actor through the actor system
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  // #2: sending messages
  simpleActor ! "Hello"

  /*
    - messages are sent asynchronously
    - many actors (in the millions) can share a few dozen threads
    - each message is processed/handled ATOMICALLY
    - no need for locks
   */

  //changing actor behaviour + stashing
  // actors can spawn other actors
  // guardians: /system, /user, / = root guardian

  // actors have a defined lifecycle: they can be started, stopped, suspended, resumed, restarted

  // stopping actors - context.stop
  simpleActor ! PoisonPill

  // logging
  // supervision

  // configure Akka infrastruture: dispatchers, routers, mailboxes.

  // schedulers
  import scala.concurrent.duration._
  import system.dispatcher

  system.scheduler.scheduleOnce(2 seconds) { simpleActor ! "Happy Birthday!"}

  // Akka patterns including FSM + ask pattern
  import akka.pattern.ask

  implicit val timeout = Timeout(3 seconds)

  val future = simpleActor ? "question"

  // the pipe pattern
  import akka.pattern.pipe

  val anotherActor = system.actorOf(Props[SimpleActor],"anotherSimpleActor")
  future.mapTo[String].pipeTo(anotherActor)

}
