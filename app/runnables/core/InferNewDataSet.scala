package runnables.core

import javax.inject.Inject

import dataaccess.{FieldTypeHelper, FieldTypeInferrerFactory}
import models.StorageType
import models.DataSetSetting
import runnables.InputFutureRunnable
import services.DataSetService

import scala.reflect.runtime.universe.typeOf

class InferNewDataSet @Inject()(dataSetService: DataSetService) extends InputFutureRunnable[InferNewDataSetSpec] {

  override def runAsFuture(spec: InferNewDataSetSpec) = {
    val fieldTypeInferrerFactory = FieldTypeInferrerFactory(
      FieldTypeHelper.fieldTypeFactory(booleanIncludeNumbers = spec.booleanIncludeNumbers),
      spec.maxEnumValuesCount,
      spec.minAvgValuesPerEnum,
      FieldTypeHelper.arrayDelimiter
    )

    val dataSetSetting = new DataSetSetting(spec.newDataSetId, spec.storageType)

    dataSetService.translateDataAndDictionaryOptimal(
      spec.originalDataSetId,
      spec.newDataSetId,
      spec.newDataSetName,
      Some(dataSetSetting),
      None,
      spec.saveBatchSize,
      spec.inferenceGroupSize,
      spec.inferenceGroupsInParallel,
      Some(fieldTypeInferrerFactory.applyJson)
    )
  }

  override def inputType = typeOf[InferNewDataSetSpec]
}

case class InferNewDataSetSpec(
  originalDataSetId: String,
  newDataSetId: String,
  newDataSetName: String,
  storageType: StorageType.Value,
  saveBatchSize: Option[Int],
  inferenceGroupSize: Option[Int],
  inferenceGroupsInParallel: Option[Int],
  maxEnumValuesCount: Int,
  minAvgValuesPerEnum: Double,
  booleanIncludeNumbers: Boolean
)