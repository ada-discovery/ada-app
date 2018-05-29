package runnables.denopa

import javax.inject.Inject

import models.{DataSetSetting, StorageType}
import runnables.{FutureRunnable, GuiceBuilderRunnable}
import runnables.luxpark.DataSetId._
import services.DataSetService

class CleanupDeNoPaBaseline @Inject()(dataSetService: DataSetService) extends FutureRunnable {
  override def runAsFuture =
    dataSetService.translateDataAndDictionary(
      denopa_raw_clinical_baseline,
      denopa_clinical_baseline,
      "Clinical Baseline",
      Some(new DataSetSetting(denopa_clinical_baseline, StorageType.ElasticSearch)),
      None,
      true, true, true
    )
}

class CleanupDeNoPaFirstVisit @Inject()(dataSetService: DataSetService) extends FutureRunnable {
  override def runAsFuture =
    dataSetService.translateDataAndDictionary(
      denopa_raw_clinical_first_visit,
      denopa_clinical_first_visit,
      "Clinical First Visit",
      Some(new DataSetSetting(denopa_clinical_first_visit, StorageType.ElasticSearch)),
      None,
      true, true, true
    )
}

class CleanupDeNoPaSecondVisit @Inject()(dataSetService: DataSetService) extends FutureRunnable {
  override def runAsFuture =
    dataSetService.translateDataAndDictionary(
      denopa_raw_clinical_second_visit,
      denopa_clinical_second_visit,
      "Clinical Second Visit",
      Some(new DataSetSetting(denopa_clinical_second_visit, StorageType.ElasticSearch)),
      None,
      true, true, true
    )
}

object CleanupDeNoPaBaseline extends GuiceBuilderRunnable[CleanupDeNoPaBaseline] with App { run }
object CleanupDeNoPaFirstVisit extends GuiceBuilderRunnable[CleanupDeNoPaFirstVisit] with App { run }
object CleanupDeNoPaSecondVisit extends GuiceBuilderRunnable[CleanupDeNoPaSecondVisit] with App { run }