import akka.actor.{ActorSystem, Props}
import src.main.scala.Server
import src.main.scala.Server.{Connect, Send, User}

/**
  * Created by ashu on 3/22/2017.
  */
object ChatApp extends App{
  val system = ActorSystem("System")
  val server = system.actorOf(Props[Server])

  val c1 = system.actorOf(Props(new Client(server)))
  val c2 = system.actorOf(Props(new Client(server)))

  val ashu:User = User("Ashu",c1)
  val none:User = User("None",c2)

  server ! Connect(ashu,none)

  server ! Send(ashu,none, "Hello")
}
