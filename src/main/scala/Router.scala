import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
/**
  * Created by ashu on 3/25/2017.
  */
object Router {
    def route(implicit actorSystem:ActorSystem):Route = {
      path("cht"){
        get{
          handleWebSocketMessages(ChatEvent.)
        }
      }
    }
}
