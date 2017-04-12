

import java.io.Serializable

case class Work(workId: String, job: Any) extends Serializable

case class WorkResult(workId: String, result: Any)