package runnables

import scala.reflect.runtime.universe.typeOf

class DeleteRecord extends DsaInputFutureRunnable[RecordSpec] {

  override def runAsFuture(spec: RecordSpec) = dataSetRepo(spec.dataSetId).delete(spec.recordId)

  override def inputType = typeOf[RecordSpec]
}