/*
        [String,String]       [String,Node]            [Node,Post]
         +-----------+         +---------------+       +-------------+
         |           |         |               |       |             |
         |           |         |  toNode       +------>|    toPost   |
         |  Source   +-------->+               |       |             |
         |           |         |               |       |             |
         +-----------+         +---------------+       +---+---------+
                                                           |
                                                           |
                                                           v
        +-----------------------------------------------------------+
        |                                                           |
        |            broadcast                                      +------+
        |                                                           |      |
        ++------------+-----------------+-----------------+---------+      |
         |            |                 |                 |                |
         |            |                 |                 |                |
         |            |                 |                 |                |
   +     v            v                 v                 v                v
  ++-----+---+    +---+--------+     +--+-------+    +----+--------+     +-+------------+
  |          |    |            |     |          |    |             |     |              |
  |toAnswer  |    |toComment   |     |toFavorite|    |toAnswer     |     |toQuestion    |
  |          |    |Count       |     |Count     |    |Count        |     |              |
  +--+-------+    +---+--------+     +--+-------+    +--+-^--------+     +------+-------+
     |                |                 |               |                       |
     |                |                 |               |                       |
     v                v                 v               v                       v
 +---v----------------+-----------------+---------------+---------+      +------+-----+
 |                                                                +<-----+            |
 |       Merge                                                    |      | question   |
 |                                                                |      | Broadcast  |
 +--------+-------------------------------------------------------+      +------+-----+
          |                                                                     |
          v                                                                     v
+---------+--+      +------------+                                      +-------+----+
|            |      |            |             +--------------+         |            |
| max        +----->+ sinkFuture |             |              +<--------+ negative   |
|            |      |            |             |negativeSink  +         | Count      |
+------------+      +------------+             |              |         +------------+
                                               +--------------+
*/

import akka.Done
import akka.actor.{Actor, ActorRef, ActorSystem, Props, Status}
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.xml.{Node, XML}

/**
  * Created by ashu on 3/29/2017.
  */
object StackOverFlow extends App {

  implicit val system = ActorSystem("StackOverFlow")
  implicit val materializer = ActorMaterializer()

  sealed trait Event {
    var id: String
    var counter: Int
  }

  case class Post(var id: String,
                  postType: Int,
                  var counter: Int,
                  viewCount: Int,
                  answerCount: Int,
                  commentCount: Int,
                  favoriteCount: Int,
                  creationDate: String) extends Event

   // for pattern matching and nice output format
  case class Answer(var id: String, var counter: Int) extends Event
  case class Question(var id: String, var counter: Int) extends Event
  case class AnswerCount(var id: String, var counter: Int) extends Event
  case class CommentCount(var id: String, var counter: Int) extends Event
  case class FavoriteCount(var id: String, var counter: Int) extends Event


  def toPost(row: Node): Post = {
    Post(
      (row \ "@Id").text,
      Try((row \ "@PostTypeId").text.toInt).getOrElse(0),
      Try((row \ "@Score").text.toInt).getOrElse(0),
      Try((row \ "@ViewCount").text.toInt).getOrElse(0),
      Try((row \ "@AnswerCount").text.toInt).getOrElse(0),
      Try((row \ "@CommentCount").text.toInt).getOrElse(0),
      Try((row \ "@FavoriteCount").text.toInt).getOrElse(0),
      (row \ "@CreationDate").text
    )
  }

  val inputString = io.Source.fromFile("path/to/Posts.xml").getLines()


  // take advantage of parallelism
  val N = 8
  val toPostEvent = Flow[Node].mapAsyncUnordered(2)(row => Future(toPost(row)))
  val toAnswer = Flow[Post].filter(_.postType == 2).mapAsyncUnordered(N)(post => Future(Answer(post.id, post.counter)))
  val toQuestion = Flow[Post].filter(_.postType == 1).mapAsyncUnordered(N)(post => Future(Question(post.id, post.counter)))
  val toAnswerCount = Flow[Post].mapAsyncUnordered(N)(post => Future(AnswerCount(post.id, post.answerCount)))
  val toComment = Flow[Post].mapAsyncUnordered(N)(post => Future(CommentCount(post.id, post.commentCount)))
  val toFavoriteCount = Flow[Post].mapAsyncUnordered(N)(post => Future(FavoriteCount(post.id, post.favoriteCount)))

  // check if the line is correct
  def validRow(line: String): Boolean = line.trim.startsWith("<row")
  val toNode = Flow[String].filter(validRow).map(line => XML.loadString(line))

  def maxEvent(event1: Event, event2: Event): Event = {
    if (event1.counter > event2.counter) event1 else event2
  }
  val toMax = Flow[Event].reduce(maxEvent)

  // count total number of negative posts
  val negativeCount = Flow[Event].fold((0.0,0.0)) {
    (x: (Double,Double), y: Event) =>
      if (y.counter < 0) (x._1 + 1,x._2 + 1)
      else (x._1 + 0, x._2 + 1)
  }



  def sinkFormat(event: Event) =  event match {
    case Answer(id, counter) =>
      println(s"Top Answer id $id, Score $counter")
    case AnswerCount(id, counter) =>
      println(s"Post with highest number of answers: Id $id, Total Answer Count $counter")
    case Question(id, counter) =>
      println(s"Top Question id $id, Score $counter")
    case CommentCount(id, counter) =>
      println(s"Post with highest comment count: Id $id, Total Comment Count $counter")
    case FavoriteCount(id, counter) =>
      println(s"Most favorite post $id, Total Favorite count $counter")
    case _ => println("Not defined")
  }

  val sink = Sink.foreach(sinkFormat)
  val message = system.actorOf(Props[Message])
  val flowMonitorActor = system.actorOf(Props(new FlowMonitor("flow", message)))


  def printPost(n: Int) = Sink.fold(0) {
    (x: Int, y: Post) =>
      if (x % n == 0) println(s"[$x] Post-id ${y.id}, Type  ${y.postType}, Score ${y.counter}")
      x + 1
  }

  def printNegative(x:(Double,Double)) = println(s"Total percentage of questions with  negative score" +
    s": ${(x._1/x._2) * 100}")

  // prints the percentage of posts with negative score
  val negativeSink = Sink.foreach(printNegative)

  // data pipeline as streams
  val graph = RunnableGraph.fromGraph(GraphDSL.create(sink) {
    implicit builder =>
      sinkFuture =>
        import akka.stream.scaladsl.Source
        val source: Outlet[String] = builder.add(Source.fromIterator(() => inputString)).out
        val max: FlowShape[Event, Event] = builder.add(toMax)

        val broadcast = builder.add(Broadcast[Post](5))
        val merge = builder.add(Merge[Event](5))

        val flowMonitor = builder.add(Sink.actorRef(flowMonitorActor, Status.Success(())))

        val b2 = builder.add(Broadcast[Post](2))
        val questionBroadcast = builder.add(Broadcast[Question](2))

        import GraphDSL.Implicits._
        source ~> toNode ~> toPostEvent ~> b2 ~> broadcast ~> toAnswer ~> merge

        broadcast ~> toQuestion ~> questionBroadcast ~> merge
        questionBroadcast ~> negativeCount ~> negativeSink
        broadcast ~> toAnswerCount ~>  merge
        broadcast ~> toComment ~>  merge
        broadcast ~> toFavoriteCount ~> merge

        merge ~> max ~> sinkFuture

        b2 ~> printPost(1000)
        ClosedShape
  })

  val done: Future[Done] = graph.run()
  val start = System.nanoTime()

  done onComplete {
    case Success(value) =>
      val totalTimeTaken = (System.nanoTime() - start) / 1000000000.0
      println(s"Stream finished successfully\nTime taken $totalTimeTaken")
      System.exit(0)
    case Failure(e) =>
      println(s"Stream failed with ${e.printStackTrace()}")
      System.exit(1)
  }

  class FlowMonitor(name: String, Message: ActorRef) extends Actor {
    override def receive: Receive = {
      case Status.Success(_) => Message ! "Flow completed successfully"
      case Status.Failure(e) => Message ! s"Flow completed with failure: ${e.printStackTrace()}"
      case _ =>
    }
  }
  
  class Message extends Actor {
    override def receive: Receive = {
      case s: String => println(s)
    }
}

}
