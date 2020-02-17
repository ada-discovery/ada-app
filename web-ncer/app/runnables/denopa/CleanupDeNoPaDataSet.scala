package runnables.denopa

import javax.inject.Inject

import org.ada.server.models.{DataSetSetting, StorageType}
import org.incal.core.runnables.FutureRunnable
import org.incal.play.GuiceRunnableApp
import runnables.denopa.DataSetId._
import org.ada.server.services.DataSetService

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

object CleanupDeNoPaBaseline extends GuiceRunnableApp[CleanupDeNoPaBaseline]
object CleanupDeNoPaFirstVisit extends GuiceRunnableApp[CleanupDeNoPaFirstVisit]
object CleanupDeNoPaSecondVisit extends GuiceRunnableApp[CleanupDeNoPaSecondVisit]