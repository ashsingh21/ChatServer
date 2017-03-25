import akka.actor.{ActorRef, ActorSystem, Props}

import scala.collection.immutable.Queue

/**
  * Created by ashu on 3/25/2017.
  */
class ChatManager(actorSystem: ActorSystem) {
  val chatQueue: Queue[ChatLink] = Queue()

  def findOrCreate(actorRef: ActorRef)(implicit actorSystem: ActorSystem): Any = {
    if (chatQueue.nonEmpty) {
      val actor = chatQueue.dequeue
      // TODO add logic
    } else createUser
  }

  def createUser = {
    val chatLink = ChatLink
    chatQueue.enqueue(chatLink)
  }
}

object ChatManager {
  def apply(roomId: Int)(implicit actorSystem: ActorSystem) = new ChatManager(actorSystem)
}
