package runnables.denopa

import javax.inject.Inject

import models.CsvDataSetImportInfo
import runnables.DataSetId._
import play.api.Configuration
import runnables.GuiceBuilderRunnable
import services.DataSetService

class ImportDeNoPaSecondVisit @Inject() (configuration: Configuration, dataSetService: DataSetService) extends Runnable {

  override def run = dataSetService.importDataSet(
    CsvDataSetImportInfo (
      None,
      "DeNoPa",
      denopa_secondvisit,
      "Clinical Second Visit",
      Some (configuration.getString ("denopa.import.folder").get + "DeNoPa-v3_with_§§_and_§%w.csv"),
      "§§",
      Some("§%w"),
      None,
      false,
      None,
      Some(DeNoPaDataSetSetting.SecondVisit)
    ), None
  )
}

object ImportDeNoPaSecondVisit extends GuiceBuilderRunnable[ImportDeNoPaSecondVisit] with App { run }