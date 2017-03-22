import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, Terminated}

/**
  * Created by ashu on 3/21/2017.
  */
object ChatServer extends App{

  sealed trait Message

  case class Send(msg: String) extends Message

  case class NewMessage(from: String, msg: String) extends Message

  case class Info(msg: String) extends Message

  case class Connect(username: String) extends Message

  case class Broadcast(msg: String) extends Message

  case object Disconnect extends Message

  class ChatServer extends Actor {
    private var clients = List[(String, ActorRef)]()

    override def receive: Receive = {
      case Connect(username) =>
        broadcast(Info(f"$username joined the chat"))
        clients = (username, sender()) :: clients
        context.watch(sender())
      case Broadcast(msg) =>
        val userName = getUsername(sender())
        broadcast(NewMessage(userName, msg))
      case Terminated(client) =>
        val username = getUsername(client)
        clients = clients.filter(sender() != _._2)
        broadcast(Info(f"$username left the chat"))
    }

    def broadcast(msg:Message) = {
      clients.foreach(x => x._2 ! msg)
    }

    def getUsername(actor:ActorRef) = {
      clients.filter(actor == _._2).head._1
    }
  }

  class Client(val username: String, server: ActorRef) extends Actor{

    server ! Connect(username)

    override def receive: Receive = {
      case NewMessage(from, msg) =>
        println(f"[$username%s's client] - $from%s: $msg%s")
      case Send(msg) => server ! Broadcast(msg)
      case Info(msg) =>
        println(f"[$username%s's client] - $msg%s")
      case Disconnect =>
        self ! PoisonPill
    }
  }

  val system = ActorSystem("System")
  val server = system.actorOf(Props[ChatServer])

 }
