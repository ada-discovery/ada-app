package runnables

import javax.inject.Inject

import dataaccess.{FieldTypeHelper, FieldTypeInferrerFactory}
import models.DataSetMetaInfo
import runnables.DataSetId._
import services.DataSetService

import scala.concurrent.Await.result
import scala.concurrent.duration._

class InferPPMIClinicalDataSet @Inject()(dataSetService: DataSetService) extends FutureRunnable {

  override def runAsFuture = {
    val fieldTypeInferrerFactory = FieldTypeInferrerFactory(
      FieldTypeHelper.fieldTypeFactory(),
      50,
      10,
      FieldTypeHelper.arrayDelimiter
    )

    dataSetService.translateDataAndDictionaryOptimal(
      "ppmi.raw_clinical_visit",
      "ppmi.clinical_visit",
      "Clinical Visit",
      None,
      None,
      Some(100),
      Some(fieldTypeInferrerFactory.applyJson)
    )
  }
}

object InferPPMIClinicalDataSet extends GuiceBuilderRunnable[InferPPMIClinicalDataSet] with App { run }