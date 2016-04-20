package runnables.denopa

import javax.inject.Inject

import runnables.DataSetId._
import play.api.Configuration
import runnables.{DataSetImportInfo, GuiceBuilderRunnable}
import services.DataSetService

class ImportDeNoPaSecondVisit @Inject() (configuration: Configuration, dataSetService: DataSetService) extends Runnable {

  override def run = dataSetService.importDataSet(
    DataSetImportInfo (
    "DeNoPa",
    denopa_secondvisit,
    "Clinical Second Visit",
    Some (configuration.getString ("denopa.import.folder").get + "DeNoPa-v3_with_§§_and_§%w.csv"),
    None,
    "§§",
    Some("§%w"),
    None,
    Some(DeNoPaDataSetSetting.SecondVisit)
  ))
}

object ImportDeNoPaSecondVisit extends GuiceBuilderRunnable[ImportDeNoPaSecondVisit] with App { run }