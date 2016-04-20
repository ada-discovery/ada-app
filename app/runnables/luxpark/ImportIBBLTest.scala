package runnables.luxpark

import javax.inject.Inject

import runnables.DataSetId.ibbl_test
import play.api.Configuration
import runnables.{DataSetImportInfo, GuiceBuilderRunnable}
import services.DataSetService

class ImportIBBLTest @Inject() (configuration: Configuration, dataSetService: DataSetService) extends Runnable {

  override def run = dataSetService.importDataSet(
    DataSetImportInfo(
      "Lux Park",
      ibbl_test,
      "IBBL (Test) BioSamples",
      Some(configuration.getString("ibbl.import.folder").get + "140174_ND_TEST_LCSB_20160404.csv"),
      None,
      ",",
      None,
      Some("ISO-8859-1"),
      Some(LuxParkDataSetSetting.IBBLTest)
    )
  )
}

object ImportIBBLTest extends GuiceBuilderRunnable[ImportIBBLTest] with App { run }