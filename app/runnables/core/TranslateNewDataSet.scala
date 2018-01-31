package runnables.core

import javax.inject.Inject

import dataaccess.{FieldTypeHelper, FieldTypeInferrerFactory}
import models.StorageType
import models.DataSetSetting
import runnables.InputFutureRunnable
import services.DataSetService

import scala.reflect.runtime.universe.typeOf

class TranslateNewDataSet @Inject()(dataSetService: DataSetService) extends InputFutureRunnable[TranslateNewDataSetSpec] {

  override def runAsFuture(spec: TranslateNewDataSetSpec) = {
    val dataSetSetting = new DataSetSetting(spec.newDataSetId, spec.storageType, spec.defaultDistributionFieldName)

    dataSetService.translateData(
      spec.originalDataSetId,
      spec.newDataSetId,
      spec.newDataSetName,
      Some(dataSetSetting),
      None,
      spec.saveBatchSize
    )
  }

  override def inputType = typeOf[TranslateNewDataSetSpec]
}

case class TranslateNewDataSetSpec(
  originalDataSetId: String,
  newDataSetId: String,
  newDataSetName: String,
  storageType: StorageType.Value,
  defaultDistributionFieldName: String,
  saveBatchSize: Option[Int]
)