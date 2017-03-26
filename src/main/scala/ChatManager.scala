import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.collection.immutable.Queue

/**
  * Created by ashu on 3/26/2017.
  */
object ChatManager {
  //queue to connect two random users
  var userQueue: Queue[ChatService] = Queue()

  // if no user create a chat service else conenct the user to exisiting chat service
  def createChat(implicit actorSystem: ActorSystem,actorMaterializer: ActorMaterializer): ChatService = {
    if (userQueue.nonEmpty) {
      val dequeu:(ChatService,Queue[ChatService]) = userQueue.dequeue
      userQueue = dequeu._2
      dequeu._1
    } else {
      val chatService = ChatService()
      userQueue = userQueue.enqueue(chatService)
      chatService
    }
  }
}
