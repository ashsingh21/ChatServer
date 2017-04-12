/**
  * Created by ashu on 4/10/2017.
  */
object MessageProtocol {

  //from workers
  case class RequestWork(workerId:String)
  case class RegisterWorker(workerId:String)
  case class WorkIsDone(workId:String, workerId: String, result:Any)
  case class WorkFailed(wordId:String,workerId:String)
  // from master
  case object WorkIsReady
  case class Ack(id:String)
}
