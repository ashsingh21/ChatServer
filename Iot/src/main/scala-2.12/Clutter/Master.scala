import Master.{Busy, CleanupTick, Idle, WorkerState}
import akka.actor.{ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.persistence.PersistentActor

import scala.concurrent.duration.{Deadline, FiniteDuration}

/**
  * Created by ashu on 4/10/2017.
  */

object Master {
  def props(timeOut: FiniteDuration): Props = Props(classOf[Master], timeOut)

  val ProcessedResult = "results"

  case class Ack(workId: String)

  sealed trait WorkerStatus

  case object Idle extends WorkerStatus
  case class Busy(workId: String, deadline: Deadline) extends WorkerStatus
  case class WorkerState(actorRef: ActorRef, status: WorkerStatus)

  private object CleanupTick

}

class Master(timeOut: FiniteDuration) extends PersistentActor with ActorLogging {

  import Master._

  val mediator = DistributedPubSub(context.system).mediator
  ClusterClientReceptionist(context.system).registerService(self) // register to cluster client

  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) => role + "-master"
    case None => "maste  r"
  }

  import context.dispatcher

  val cleanupTask = context.system.scheduler.schedule(timeOut / 2, timeOut / 2, self, CleanupTick)
  override def postStop(): Unit = cleanupTask.cancel()

  import WorkState._

  private var workers = Map[String, WorkerState]()
  private var workState = WorkState.empty

  override def receiveRecover: Receive = {
    case event: WorkDomainEvent =>
      workState.updated(event)
      log.info("Cluster Master -> Replayed event: {}", event.getClass.getSimpleName)
  }

  override def receiveCommand: Receive = {
    case MessageProtocol.RegisterWorker(workerId) =>
      if (workers.contains(workerId)) {
        workers += (workerId -> workers(workerId).copy(actorRef = sender()))
      } else {
        log.info("Cluster Master -> Worker registered: Worker Id {}", workerId)
        workers += (workerId -> WorkerState(sender(), Idle))
        if (workState.hasWork) sender() ! MessageProtocol.WorkIsReady
      }

    case MessageProtocol.RequestWork(workerId) =>
      if (workState.hasWork) {
        workers.get(workerId) match {
          case Some(s@WorkerState(_, Idle)) =>
            val work = workState.nextWork
            persist(WorkStarted(work.workId)) { event =>
              workState = workState.updated(event)
              log.info("Cluster Master -> Delegating work to: Worker Id {} | Work Id {} | Device Id {}", workerId, work.workId)
              workers += (workerId -> s.copy(status = Busy(work.workId, Deadline.now + timeOut)))
              sender() ! work
            }
          case _ =>
        }
      }

    case MessageProtocol.WorkIsDone(workerId, workId, result) =>
      if (workState.isDone(workerId)) {
        sender ! MessageProtocol.Ack(workId)
      } else if (workState.isInProgress(workId)) {
        log.info("Cluster Master -> ALERT: Work NOT IN PROGRESS! Work Id {} | Worker Id {}", workId, workerId)
      } else {
        changeWorkerToIdle(workerId, workId)
        persist(WorkCompleted(workId, result)) { event =>
          workState = workState.updated(event)
          mediator ! DistributedPubSubMediator.Publish(ProcessedResult, WorkResult(workId, result))
          sender ! Ack(workId)
        }
      }

    case MessageProtocol.WorkFailed(workerId, wordId) =>
      if (workState.isInProgress(wordId))
        log.info("Cluster Master -> ALERT: Work FAILED! Work Id {} | Worker Id {}", wordId, workerId)
      changeWorkerToIdle(workerId, wordId)
      persist(WorkerFailed(wordId)) { event =>
        workState = workState.updated(event)
        notifyWorkers()
      }

    case work:Work =>
      if(workState.isAccepted(work.workId)){
        sender() ! Master.Ack(work.workId)
      } else {
        log.info("Cluster Master -> Accepted work: Work Id {} | Device Id {}", work.workId, "add id here")
        persist(WorkAccepted(work)){event =>
          workState = workState.updated(event)
          sender ! Master.Ack(work.workId)
          notifyWorkers()
        }
      }

    case CleanupTick =>
      for((workerId, s @ WorkerState(_,Busy(workId,timeOut))) <- workers){
        if(timeOut.isOverdue()){
          log.info("Cluster Master -> ALERT: TIMED OUT! Work Id {}", workId)
          workers -= workerId
          persist(WorkerTimedOut(workId)){event =>
            workState = workState.updated(event)
            notifyWorkers()
          }
        }
      }
  }

  def notifyWorkers():Unit =
    if(workState.hasWork){
      workers.foreach{
        case(_,WorkerState(actorRef,Idle)) =>
          actorRef ! MessageProtocol.WorkIsReady
        case _ => //busy
      }
    }

  def changeWorkerToIdle(workerId:String, workId:String): Unit = {
    workers.get(workerId) match {
      case Some(s @ WorkerState(_, Busy(`workId`, _))) ⇒
        workers += (workerId -> s.copy(status = Idle))
      case _ ⇒
      // ok, might happen after standby recovery, worker state is not persisted
    }
  }
}


