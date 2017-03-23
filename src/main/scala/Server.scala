package src.main.scala
import akka.actor.{Actor, ActorRef, PoisonPill}

/**
  * Created by ashu on 3/22/2017.
  */


object Server {
  trait Event
  case class User(username: String, entity: ActorRef)
  case class Send(who:User,to: User, msg: String) extends Event
  case class Connect(who: User, to: User) extends Event
  case class Disconnect(who:User,from: User) extends Event
  case class Message(msg: String) extends Event
  case class UserConnect(actor: ActorRef) extends Event
}


class Server extends Actor{
    import Server._
    var users:Set[(ActorRef)] = Set()
    var sessions:Map[(User,User),Boolean] = Map()

    override def receive: Receive = {
      case Send(who,to, msg) =>
        if(sessions.contains((who,to))) to.entity ! Message(f"[${who.username}] $msg")
        else sender() ! Message("Please Connect first")
      case Connect(who,to) =>
        to.entity ! Message(f"${who.username} connected with you")
        sessions = sessions + ((who,to) -> true)
      case Disconnect(who,from) =>
        if(!sessions.contains((who,from))) sender() ! Message(s"You are not connected with ${from.username}")
        else {
          from.entity ! Message(f"${who.username} disconnected from you")
          sessions = sessions - ((who, from))
          who.entity ! PoisonPill
        }
      case UserConnect(user) =>
        sender() ! Message("You are connected to the Skynet now :)")
        users = users + user
    }
  }

