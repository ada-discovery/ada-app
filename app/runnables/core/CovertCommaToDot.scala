package runnables.core

import javax.inject.Inject

import runnables.InputFutureRunnable
import scala.reflect.runtime.universe.typeOf

class ConvertCommaToDot @Inject()(replaceString: ReplaceString) extends InputFutureRunnable[ConvertCommaToDotSpec] {

  override def runAsFuture(spec: ConvertCommaToDotSpec) =
    replaceString.runAsFuture(ReplaceStringSpec(spec.dataSetId, spec.fieldName, spec.batchSize, ",", "."))

  override def inputType = typeOf[ConvertCommaToDotSpec]
}

case class ConvertCommaToDotSpec(dataSetId: String, fieldName: String, batchSize: Int)