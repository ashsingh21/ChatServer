import akka.actor.ActorRef

/**
  * Created by ashu on 3/25/2017.
  */
object ChatEvent {
  sealed trait ChatEvent
  case class ChatMessage(msg:String) extends ChatEvent
  case class IncomingMessage(override val msg:String) extends ChatMessage(msg) with ChatEvent
  case object Logout extends ChatEvent
  case object UserJoined extends ChatEvent

}
