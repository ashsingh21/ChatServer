import ChatEvent._
import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.{ClosedShape, OverflowStrategy}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}


/**
  * Created by ashu on 3/25/2017.
  */
class ChatLink(actorSystem: ActorSystem) {

  private[this] val chatLinkActor = actorSystem.actorOf(Props(classOf[ChatLinkActor]))

  def websocketFlow = {
    RunnableGraph.fromGraph(GraphDSL.create() {
      import GraphDSL.Implicits._
      implicit builder =>

        val fromWebsocket = builder.add(
          Flow[Message].collect {
            case TextMessage.Strict(txt) => IncomingMessage(txt)
          })

        val toWebsocket = builder.add({
          Flow[IncomingMessage].map({
            case IncomingMessage(msg) => TextMessage(msg)
          })
        })

        val chatSource = Source.actorRef[IncomingMessage](bufferSize = 5, OverflowStrategy.fail)
        //send messages to the actor, if sent also UserLeft(user) before stream completes.
        val chatActorSink = Sink.actorRef[ChatEvent](chatLinkActor, Logout)

        //merges both pipes
        val merge = builder.add(Merge[ChatEvent](2))

        //Materialized value of Actor who sits in the chatroom
        val actorAsSource = builder.materializedValue.map(actor => UserJoined)


        //Message from websocket is converted into IncomingMessage and should be sent to everyone in the room
        fromWebsocket ~> merge.in(0)

        //If Source actor is just created, it should be sent as UserJoined and registered as particiant in the room
        actorAsSource ~> merge.in(1)

        merge ~> chatActorSink

        chatSource ~> toWebsocket

        ClosedShape
    }).run()

  }

  def sendMessage(message: IncomingMessage): Unit = chatLinkActor ! message
}


object ChatLink {
  def apply(roomId: Int)(implicit actorSystem: ActorSystem) = new ChatLink(actorSystem)
}


