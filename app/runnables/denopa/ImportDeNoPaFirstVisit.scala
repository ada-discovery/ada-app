package runnables.denopa

import javax.inject.Inject

import models.CsvDataSetImportInfo
import runnables.DataSetId._
import play.api.Configuration
import runnables.GuiceBuilderRunnable
import services.DataSetService

class ImportDeNoPaFirstVisit @Inject()  (configuration: Configuration, dataSetService: DataSetService) extends Runnable {

  override def run = dataSetService.importDataSet(
    CsvDataSetImportInfo(
      None,
      "DeNoPa",
      denopa_firstvisit,
      "Clinical First Visit",
      Some(configuration.getString("denopa.import.folder").get + "DeNoPa-v2_with_§§.csv"),
      "§§",
      None,
      None,
      Some(DeNoPaDataSetSetting.FirstVisit)
    ), None
  )
}

object ImportDeNoPaFirstVisit extends GuiceBuilderRunnable[ImportDeNoPaFirstVisit] with App { run }