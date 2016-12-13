package runnables

import javax.inject.Inject

import dataaccess.{FieldTypeHelper, FieldTypeInferrerFactory}
import models.DataSetMetaInfo
import runnables.DataSetId._
import services.DataSetService

import scala.concurrent.Await.result
import scala.concurrent.duration._

class InferPPMIClinicalDataSet @Inject()(dataSetService: DataSetService) extends Runnable {
  override def run = {

    val fieldTypeInferrerFactory = FieldTypeInferrerFactory(
      FieldTypeHelper.fieldTypeFactory,
      50,
      10,
      FieldTypeHelper.arrayDelimiter
    )

    result(
      dataSetService.translateDataAndDictionaryOptimal(
        "ppmi.raw_clinical_visit",
        "ppmi.clinical_visit",
        "Clinical Visit",
        None,
        None,
        Some(fieldTypeInferrerFactory.applyJson)
      ),
      30 minutes
    )
  }
}

object InferPPMIClinicalDataSet extends GuiceBuilderRunnable[InferPPMIClinicalDataSet] with App { run }