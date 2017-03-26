import akka.actor.ActorRef

/**
  * Created by ashu on 3/26/2017.
  */
object Events {

  trait ChatEvent

  case class User(name: String) extends ChatEvent

  case class UserWithActor(name: String, actor: ActorRef) extends ChatEvent

  case class UserJoined(user: UserWithActor) extends ChatEvent

  case class UserLeft(user: User) extends ChatEvent

  case class SystemMessage(msg: String) extends ChatEvent

  case class IncomingMessage(user: User, msg: String) extends ChatEvent
}
