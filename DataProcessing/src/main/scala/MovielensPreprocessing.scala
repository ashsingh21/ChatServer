import akka.actor.ActorSystem
import akka.stream._
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.{Flow, GraphDSL, Sink, Source}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import ExecutionContext.Implicits.global

/**
  * Created by ashu on 3/27/2017.
  */
object MovielensPreprocessing extends App {

  implicit val system = ActorSystem("data-system")
  implicit val materializer = ActorMaterializer()

  case class MovieEvent(userId: String, movieId: String, rating: String, timestamp: String)

  case class Rating(movieID: Int, rating: Double)

  val inputData: Iterator[String] = io.Source.fromFile("C:/Users/ashu/Desktop/ml-20m/ratingsBig.csv").getLines()

  def inputToMovieEvent(input: Array[String]) = MovieEvent(input(0),
    input(1), input(2), input(3))

  val averageRating = Flow[Rating]
    .groupBy(100000, _.movieID)
    .fold((0.0, 0)) {
      (x: (Double, Int), y: Rating) =>
        val rating = y.rating + x._1
        (rating, y.movieID)
    }.mergeSubstreams


  val toDoubleRating = Flow[MovieEvent].mapAsyncUnordered(4) { r =>
    Future(Rating(Try(r.movieId.toInt).getOrElse(0), Try(r.rating.toDouble).getOrElse(0)))
  }


  def sinkFormat[A](a: A) = {
    a match {
      case (rating: Double, movieID: Int) => println(s"Movie Id: $movieID, Rating: $rating")
      case Rating(movieID, rating) => println(s"Movie Id: $movieID, Rating: $rating")
      case _ => println("Unknown")
    }
  }

  val csvToMovieEvent = Flow[String].map(x => x.split(",").map(_.trim)).map(inputToMovieEvent)

  val graph = RunnableGraph.fromGraph(GraphDSL.create() {
    implicit builder =>

      val input: Outlet[String] = builder.add(Source.fromIterator(() => inputData).take(100)).out
      val csvToMovie: FlowShape[String, MovieEvent] = builder.add(csvToMovieEvent)
      val toRating: FlowShape[MovieEvent, Rating] = builder.add(toDoubleRating)

      //  val average:FlowShape[Rating,(Double,Int)] = builder.add(averageRating)
      val sink: Inlet[Any] = builder.add(Sink.foreach(sinkFormat)).in
      import GraphDSL.Implicits._
      input ~> csvToMovie ~> toRating ~> sink

      ClosedShape
  })

  graph.run(materializer)

}
