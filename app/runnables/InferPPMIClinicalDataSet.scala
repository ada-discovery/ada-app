package runnables

import javax.inject.Inject

import models.DataSetMetaInfo
import runnables.DataSetId._
import services.DataSetService

import scala.concurrent.Await.result
import scala.concurrent.duration._

class InferPPMIClinicalDataSet @Inject()(dataSetService: DataSetService) extends Runnable {
  override def run =
    result(
      dataSetService.translateDataAndDictionaryOptimal(
        "ppmi.raw_clinical_visit",
        DataSetMetaInfo(None, "ppmi.clinical_visit", "Clinical Visit", 0, false, None),
        None,
        None
      ),
      30 minutes
    )
}

object InferPPMIClinicalDataSet extends GuiceBuilderRunnable[InferPPMIClinicalDataSet] with App { run }