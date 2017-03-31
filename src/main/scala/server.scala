import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.io.StdIn

/**
  * Created by ashu on 3/26/2017.
  */
object server extends App{

  import akka.http.scaladsl.server.Directives._

  implicit val system = ActorSystem("chat-system")
  implicit val materializer = ActorMaterializer()


  // route ws://host:port/chat?usernmame=name
  val route = path("chat") {
    get {
      parameter("username")(username =>
        handleWebSocketMessages(ChatManager.createChat.chatFlow(username))
      )

    }
  }

  val (host, port) = ("localhost", 8080)

  val bindingFuture = Http().bindAndHandle(route, host, port)
  println(s"Server is now online at http://$host:$port\nPress RETURN to stop...")
  StdIn.readLine()

  import system.dispatcher

  bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())
  println("Server is down...")

}
