import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._

/**
  * Created by ashu on 3/24/2017.
  */
object Test extends App {

  final case class Item(name: String, id: Long)

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()

  val (host, port) = ("localhost", 8080)

  val route =
    path("chat"){

    }

  val bindingFuture = Http().bindAndHandle(route, host, port)
}
   