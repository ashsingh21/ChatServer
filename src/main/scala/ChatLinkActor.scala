import ChatEvent.{IncomingMessage, Logout, UserJoined}
import akka.actor.{Actor, ActorRef, PoisonPill}


/**
  * Created by ashu on 3/25/2017.
  */
class ChatLinkActor extends Actor {
  var users: List[ActorRef] = List()

  override def receive: Receive = {
    case IncomingMessage(msg) =>
      send(sender(), msg)
    case Logout =>
      sender() ! PoisonPill
      self ! PoisonPill
      println("user left, ending chat")
    case UserJoined =>
      println("Contgrats you got a user")
      users = users :+ sender()
  }

  def send(from: ActorRef, msg: String) = {
    for (
      x <- users
      if x != sender()
    ) yield x ! IncomingMessage(msg)
  }
}
