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
        DataSetMetaInfo(None, denopa_clinical_baseline, "Clinical Baseline", 0, false, None),
        Some(DeNoPaDataSetSettings.ClinicalBaseline),
        true, true
      ),
      2 minutes
    )
}

class CleanupDeNoPaFirstVisit @Inject()(dataSetService: DataSetService) extends Runnable {
  override def run =
    result(
      dataSetService.translateDataAndDictionary(
        denopa_raw_clinical_first_visit,
        DataSetMetaInfo(None, denopa_clinical_first_visit, "Clinical First Visit", 1, false, None),
        Some(DeNoPaDataSetSettings.ClinicalFirstVisit),
        true, true
      ),
      2 minutes
    )
}

class CleanupDeNoPaSecondVisit @Inject()(dataSetService: DataSetService) extends Runnable {
  override def run =
    result(
      dataSetService.translateDataAndDictionary(
        denopa_raw_clinical_second_visit,
        DataSetMetaInfo(None, denopa_clinical_second_visit, "Clinical Second Visit", 2, false, None),
        Some(DeNoPaDataSetSettings.ClinicalSecondVisit),
        true, true
      ),
      2 minutes
    )
}

object CleanupDeNoPaBaseline extends GuiceBuilderRunnable[CleanupDeNoPaBaseline] with App { run }
object CleanupDeNoPaFirstVisit extends GuiceBuilderRunnable[CleanupDeNoPaFirstVisit] with App { run }
object CleanupDeNoPaSecondVisit extends GuiceBuilderRunnable[CleanupDeNoPaSecondVisit] with App { run }