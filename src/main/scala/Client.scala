import akka.actor.{Actor, ActorRef}
import src.main.scala.Server.{Message,User, UserConnect}



/**
  * Created by ashu on 3/22/2017.
  */
class Client(server:ActorRef) extends Actor{
  server ! UserConnect(self)

  override def receive: Receive = {
      case Message(msg) =>
        println(msg)
    }
}
