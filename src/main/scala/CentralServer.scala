import akka.actor.{Actor, ActorRef}

/**
  * Created by ashu on 3/22/2017.
  */
class CentralServer {
  trait Event
  case class User(username:String, entity: ActorRef)
  case class Send(from:User,to:User, msg:String) extends Event
  case class Connect(who: User,to: User) extends Event
  case class Disconnect(who: User, from: User) extends Event
  case class Message(msg:String) extends Event

  class Server extends Actor{
    var sessions:Map[(User,User),Boolean] = Map()

    override def receive: Receive = {
      case Send(from,to, msg) =>
        if(sessions.contains((from,to))) to.entity ! Message(msg)
        else sender() ! Message("Please Connect first")
      case Connect(who,to) =>
        to.entity ! Message(f"${who.username} connected with you")
        sessions = sessions + ((who,to) -> true)
    }
  }
}
