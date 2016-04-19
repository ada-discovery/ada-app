package runnables.denopa

import javax.inject.Inject

import models.DataSetId._
import play.api.Configuration
import runnables.{DataSetImportInfo, GuiceBuilderRunnable}
import services.DataSetService

class ImportDeNoPaFirstVisit @Inject()  (configuration: Configuration, dataSetService: DataSetService) extends Runnable {

  override def run = dataSetService.importDataSet(
    DataSetImportInfo(
      "DeNoPa",
      denopa_firstvisit,
      "DeNoPa First Visit",
      Some(configuration.getString("denopa.import.folder").get + "DeNoPa-v2_with_§§.csv"),
      None,
      "§§",
      None,
      Some(DeNoPaDataSetSetting.FirstVisit)
    )
  )
}

object ImportDeNoPaFirstVisit extends GuiceBuilderRunnable[ImportDeNoPaFirstVisit] with App { run }