import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString
import com.typesafe.config.Config
import io.scalac.amqp.{Connection, Message}

import scala.concurrent.duration._
import scala.util.Random

/**
  * Created by ashu on 4/11/2017.
  */
object Iot extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  sealed trait Event

  case class LinearAcceleration(x: Double, y: Double, z: Double)

  case class Accelerometer(deviceId: String, linearAcc: LinearAcceleration) extends Event

  object Finished extends Event

  case class AccelerometerFeatures(meanX: Double,
                                   meanY: Double,
                                   meanZ: Double)

  // convert byte sequence to string
  def seqToString(msg: IndexedSeq[Byte]) = new String(msg.toArray)

  // helper function to transform string to Accelerometer event
  def accelerometer(msg: String): Accelerometer = {
    val xyz = """(\d+)_(\d+\.\d+)_(\d+\.\d+)_(\d+\.\d+)""".r
    msg match {
      case xyz(deviceId, x, y, z) =>
        Accelerometer(deviceId, LinearAcceleration(x.toDouble, y.toDouble, z.toDouble))
      case _ => Accelerometer("-1", LinearAcceleration(0.0, 0.0, 0.0)) //quick way to remove E values
    }
  }

  // Actor for feature processing
  class FeatureActor extends Actor {
    val count = 64
    var accReadings: List[Accelerometer] = List()
    var acc: Map[String, List[Accelerometer]] = Map()

    override def receive: Receive = {
      case s@Accelerometer(deviceId, _) =>
        acc.get(deviceId) match {
          case Some(list) =>
            if (list.size < count) {
              val updatedList = s :: list
              acc += (deviceId -> updatedList)
            } else {
              val features = createFeatures(list)
              val emptyList: List[Accelerometer] = List()
              acc += (deviceId -> emptyList)
              printFeatures(deviceId, features)
            }
          case None => {
            val list: List[Accelerometer] = List(s)
            acc += (deviceId -> list)
          }
        }
      case Finished => {
        println("Streaming Finsished")
      }
    }

    def printFeatures(deviceId: String, features: AccelerometerFeatures): Unit = {
      println(s"Accelerometer Features: Device Id: $deviceId, X: ${features.meanX}, Y: ${features.meanY}, Z: ${features.meanZ}")
    }

    // calculate average to accelerometer readings
    def createFeatures(accReadings: List[Accelerometer]): AccelerometerFeatures = {
      val (sumX, sumY, sumZ, len) = accReadings.foldLeft((0.0, 0.0, 0.0, 0)) {
        case ((x, y, z, length), reading) =>
          (x + reading.linearAcc.x, y + reading.linearAcc.y, z + reading.linearAcc.z, length + 1)
      }
      AccelerometerFeatures(sumX / len, sumY / len, sumZ / len)
    }
  }

  def generateMockdata(id: Int, limit: Int): List[String] = {
    Seq.fill(50000)(s"${Random.nextInt(id)}_${Random.nextFloat() + Random.nextInt(limit)}_${Random.nextFloat() + Random.nextInt(limit)}" +
      s"_${Random.nextFloat() + Random.nextInt(limit)}").toList
  }

  // generate random and source data
  val data = generateMockdata(5, 20)
  val source = Source(data)

  val connection = Connection()
  val queue = connection.consume(queue = "iot-acc-queue")

  // can create different queues for different sensors
  val exchange = connection.publish(exchange = "amq.direct",
    routingKey = "accelerometer")

 // create an actor for sink
  val featureActor = system.actorOf(Props[FeatureActor])
  val sink = Sink.actorRef(featureActor, Finished)

  // add  mock data to rabbitmq queue
  source.map(acc => Message(ByteString(acc))).runWith(Sink.fromSubscriber(exchange))

  // a simple akka stream
  Source.fromPublisher(queue).map(message => seqToString(message.message.body))
    .via(Flow[String].map(msg => accelerometer(msg))).runWith(sink)

}
