package runnables.denopa

import javax.inject.Inject

import runnables.DataSetId._
import play.api.Configuration
import runnables.{DataSetImportInfo, GuiceBuilderRunnable}
import services.DataSetService

class ImportDeNoPaBaseline @Inject() (configuration: Configuration, dataSetService: DataSetService) extends Runnable {

  override def run = dataSetService.importDataSet(
    DataSetImportInfo(
      "DeNoPa",
      denopa_baseline,
      "Clinical Baseline",
      Some(configuration.getString("denopa.import.folder").get + "DeNoPa-v1_with_§§.csv"),
      None,
      "§§",
      None,
      None,
      Some(DeNoPaDataSetSetting.BaseLine)
    )
  )
}

object ImportDeNoPaBaseline extends GuiceBuilderRunnable[ImportDeNoPaBaseline] with App { run }