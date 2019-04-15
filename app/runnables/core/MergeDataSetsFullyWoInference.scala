package runnables.core

import javax.inject.Inject

import org.ada.server.dataaccess.StreamSpec
import models.ml.DerivedDataSetSpec
import org.incal.core.InputFutureRunnable
import services.DataSetService

import scala.reflect.runtime.universe.typeOf

class MergeDataSetsFullyWoInference @Inject() (dataSetService: DataSetService) extends InputFutureRunnable[MergeDataSetsFullyWoInferenceSpec] {

  override def runAsFuture(input: MergeDataSetsFullyWoInferenceSpec) =
    dataSetService.mergeDataSetsFullyWoInference(
      input.sourceDataSetIds,
      input.addSourceDataSetId,
      input.resultDataSetSpec,
      input.streamSpec
    )

  override def inputType = typeOf[MergeDataSetsFullyWoInferenceSpec]
}

case class MergeDataSetsFullyWoInferenceSpec(
  sourceDataSetIds: Seq[String],
  addSourceDataSetId: Boolean,
  resultDataSetSpec: DerivedDataSetSpec,
  streamSpec: StreamSpec
)
