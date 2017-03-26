import Events._
import akka.actor.Actor

/**
  * Created by ashu on 3/26/2017.
  */
class ChatLinkActor extends Actor {

  var couple: List[UserWithActor] = List()

  override def receive: Receive = {
    case UserJoined(userWithActor) =>
      couple = couple :+ userWithActor
      userWithActor.actor ! SystemMessage(s"You are now in the ChatNet ${ChatManager.userQueue.size}!!!")
    case UserLeft(user) =>
      send(IncomingMessage(user,"left"))
    case msg: IncomingMessage =>
      send(msg)
  }

  def send(msg: IncomingMessage):Unit = {
    for(
      x <- couple
      if x.name != msg.user.name
    ) yield x.actor ! msg
  }
}
