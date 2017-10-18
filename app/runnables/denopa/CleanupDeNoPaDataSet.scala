package runnables.denopa

import javax.inject.Inject

import runnables.{FutureRunnable, GuiceBuilderRunnable}
import runnables.DataSetId._
import services.DataSetService

class CleanupDeNoPaBaseline @Inject()(dataSetService: DataSetService) extends FutureRunnable {
  override def runAsFuture =
    dataSetService.translateDataAndDictionary(
      denopa_raw_clinical_baseline,
      denopa_clinical_baseline,
      "Clinical Baseline",
      Some(DeNoPaDataSetSettings.ClinicalBaseline),
      Some(DeNoPaDataViews.ClinicalBaseline),
      true, true, true
    )
}

class CleanupDeNoPaFirstVisit @Inject()(dataSetService: DataSetService) extends FutureRunnable {
  override def runAsFuture =
    dataSetService.translateDataAndDictionary(
      denopa_raw_clinical_first_visit,
      denopa_clinical_first_visit,
      "Clinical First Visit",
      Some(DeNoPaDataSetSettings.ClinicalFirstVisit),
      Some(DeNoPaDataViews.ClinicalFirstVisit),
      true, true, true
    )
}

class CleanupDeNoPaSecondVisit @Inject()(dataSetService: DataSetService) extends FutureRunnable {
  override def runAsFuture =
    dataSetService.translateDataAndDictionary(
      denopa_raw_clinical_second_visit,
      denopa_clinical_second_visit,
      "Clinical Second Visit",
      Some(DeNoPaDataSetSettings.ClinicalSecondVisit),
      Some(DeNoPaDataViews.ClinicalSecondVisit),
      true, true, true
    )
}

object CleanupDeNoPaBaseline extends GuiceBuilderRunnable[CleanupDeNoPaBaseline] with App { run }
object CleanupDeNoPaFirstVisit extends GuiceBuilderRunnable[CleanupDeNoPaFirstVisit] with App { run }
object CleanupDeNoPaSecondVisit extends GuiceBuilderRunnable[CleanupDeNoPaSecondVisit] with App { run }