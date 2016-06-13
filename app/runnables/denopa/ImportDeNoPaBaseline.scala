package runnables.denopa

import javax.inject.Inject

import models.CsvDataSetImportInfo
import runnables.DataSetId._
import play.api.Configuration
import runnables.GuiceBuilderRunnable
import services.DataSetService

class ImportDeNoPaBaseline @Inject() (configuration: Configuration, dataSetService: DataSetService) extends Runnable {

  override def run = dataSetService.importDataSet(
    CsvDataSetImportInfo(
      None,
      "DeNoPa",
      denopa_baseline,
      "Clinical Baseline",
      Some(configuration.getString("denopa.import.folder").get + "DeNoPa-v1_with_§§.csv"),
      "§§",
      None,
      None,
      Some(DeNoPaDataSetSetting.BaseLine)
    ), None
  )
}

object ImportDeNoPaBaseline extends GuiceBuilderRunnable[ImportDeNoPaBaseline] with App { run }