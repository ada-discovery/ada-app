package runnables.denopa

import javax.inject.Inject
import models.DataSetMetaInfo
import runnables.GuiceBuilderRunnable
import runnables.DataSetId._
import services.DataSetService
import scala.concurrent.Await.result
import scala.concurrent.duration._

class CleanupDeNoPaBaseline @Inject()(dataSetService: DataSetService) extends Runnable {
  override def run =
    result(
      dataSetService.translateDataAndDictionary(
        denopa_raw_clinical_baseline,
        denopa_clinical_baseline,
        "Clinical Baseline",
        Some(DeNoPaDataSetSettings.ClinicalBaseline),
        Some(DeNoPaDataViews.ClinicalBaseline),
        true, true, true
      ),
      2 minutes
    )
}

class CleanupDeNoPaFirstVisit @Inject()(dataSetService: DataSetService) extends Runnable {
  override def run =
    result(
      dataSetService.translateDataAndDictionary(
        denopa_raw_clinical_first_visit,
        denopa_clinical_first_visit,
        "Clinical First Visit",
        Some(DeNoPaDataSetSettings.ClinicalFirstVisit),
        Some(DeNoPaDataViews.ClinicalFirstVisit),
        true, true, true
      ),
      2 minutes
    )
}

class CleanupDeNoPaSecondVisit @Inject()(dataSetService: DataSetService) extends Runnable {
  override def run =
    result(
      dataSetService.translateDataAndDictionary(
        denopa_raw_clinical_second_visit,
        denopa_clinical_second_visit,
        "Clinical Second Visit",
        Some(DeNoPaDataSetSettings.ClinicalSecondVisit),
        Some(DeNoPaDataViews.ClinicalSecondVisit),
        true, true, true
      ),
      2 minutes
    )
}

object CleanupDeNoPaBaseline extends GuiceBuilderRunnable[CleanupDeNoPaBaseline] with App { run }
object CleanupDeNoPaFirstVisit extends GuiceBuilderRunnable[CleanupDeNoPaFirstVisit] with App { run }
object CleanupDeNoPaSecondVisit extends GuiceBuilderRunnable[CleanupDeNoPaSecondVisit] with App { run }