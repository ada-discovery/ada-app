package runnables.core

import runnables.DsaInputFutureRunnable

import scala.reflect.runtime.universe.typeOf

class DeleteRecord extends DsaInputFutureRunnable[RecordSpec] {

  override def runAsFuture(spec: RecordSpec) = createDataSetRepo(spec.dataSetId).delete(spec.recordId)

  override def inputType = typeOf[RecordSpec]
}