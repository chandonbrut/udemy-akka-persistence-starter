package part3_stores_serialization

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory


// command
case class RegisterUser(email:String,name:String)

// event
case class UserRegistered(id:Int,email:String,name:String)

// serializer
class UserRegistrationSerializer extends Serializer {

  val SEPARATOR = "//"

  override def identifier:Int = 129387

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event @ UserRegistered(id,email,name) =>
      println(s"Serializing $event")
      s"[$id$SEPARATOR$email$SEPARATOR$name]".getBytes

    case _ =>
      throw new IllegalArgumentException("only usable for UserRegistered events.")
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val st = new String(bytes)
    val values = st.substring(1,st.length - 1)
    val fields = values.split(SEPARATOR)
    UserRegistered(fields(0).toInt,fields(1),fields(2))
  }

  override def includeManifest: Boolean = false
}

class UserRegistrationActor extends PersistentActor with ActorLogging {
  var currentId = 0
  override def receiveRecover: Receive = {
    case event @ UserRegistered(id,_,_) =>
      log.info(s"recovered $event")
      currentId = id
  }

  override def receiveCommand: Receive = {
    case RegisterUser(email,name) =>
      persist(UserRegistered(currentId,email,name)) { e =>
        currentId += 1
        log.info(s"Registering user [$email] [$name]")
      }
  }

  override def persistenceId: String = "user-registration"
}

object CustomSerialization extends App {

  val system = ActorSystem("CustomSerializationDemo",ConfigFactory.load().getConfig("customSerializerDemo"))

  /*
    send a command to the actor
    actor calls persist
    serializer serializes the event into bytes
    journal writes the bytes
   */

  val userRegistrationActor = system.actorOf(Props[UserRegistrationActor],"UserRegistration")

//  for (i <- 1 to 10) userRegistrationActor ! RegisterUser(s"user$i@rtjvm.com",s"User $i")
}
