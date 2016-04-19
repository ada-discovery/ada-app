package runnables.luxpark

import javax.inject.Inject

import models.DataSetId._
import play.api.Configuration
import runnables.{DataSetImportInfo, GuiceBuilderRunnable}
import services.DataSetService

class ImportIBBL @Inject() (configuration: Configuration, dataSetService: DataSetService) extends Runnable {

  override def run = dataSetService.importDataSet(
    DataSetImportInfo(
      "Lux Park",
      ibbl,
      "IBBL BioSamples",
      Some(configuration.getString("ibbl.import.folder").get + "140174_ND_STOCK_LCSB_20160404.csv"),
      None,
      ",",
      None,
      None,
      Some(LuxParkDataSetSetting.IBBL)
    )
  )
}

object ImportIBBL extends GuiceBuilderRunnable[ImportIBBL] with App { run }