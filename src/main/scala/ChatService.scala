import Events._
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}

/**
  * Created by ashu on 3/26/2017.
  */

class ChatService(implicit val actorSystem: ActorSystem,
                  implicit val actorMaterializer: ActorMaterializer) extends Directives {

  // create chat link actor to process chat events
  private val chatLinkActor = actorSystem.actorOf(Props[ChatLinkActor])

// source actors for chat events
  private val chatSource = Source.actorRef[ChatEvent](10, OverflowStrategy.fail)

  def chatFlow(username: String): Flow[Message, Message, Any] = {
    Flow.fromGraph(GraphDSL.create(chatSource) {
      import GraphDSL.Implicits._
      implicit builder =>
        chatActor =>
          // materialize the end point
          val materialization = builder.materializedValue.map(actorRef => UserJoined(UserWithActor(username, actorRef)))

          //convert incoming message to chat event
          val messageToChatEvent = builder.add(Flow[Message].collect({
            case TextMessage.Strict(msg) => IncomingMessage(User(username), msg)
          }))

          //convert chat events in the source to TextMessage
          val chatEventToMessage = builder.add(Flow[ChatEvent].map({
            case IncomingMessage(user, msg) => TextMessage(s"[${user.name}] $msg")
            case SystemMessage(msg) => TextMessage(s"[System] $msg")
          }))

          // childLinkActor at sink to process chat event, when stream completes fires up a UserLeft event
          val sink = Sink.actorRef[ChatEvent](chatLinkActor, UserLeft(User(username)))
          val merge = builder.add(Merge[ChatEvent](2))

          // check main page for graph strucuture
          materialization ~> merge ~> sink
          messageToChatEvent ~> merge

          chatActor ~> chatEventToMessage
          FlowShape(messageToChatEvent.in, chatEventToMessage.out)
    })
  }
}

//factory method
object ChatService {
  def apply()(implicit actorSystem: ActorSystem, actorMaterializer: ActorMaterializer): ChatService = new ChatService()
}


