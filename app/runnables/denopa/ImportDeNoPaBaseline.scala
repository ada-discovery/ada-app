package runnables.denopa

import javax.inject.Inject

import models.DataSetId._
import play.api.Configuration
import runnables.{DataSetImportInfo, ImportDataSet, GuiceBuilderRunnable}

class ImportDeNoPaBaseline @Inject() (configuration: Configuration) extends ImportDataSet(
  DataSetImportInfo(
    "DeNoPa",
    denopa_baseline,
    "DeNoPa Baseline",
    configuration.getString("denopa.import.folder").get + "Denopa-V1-BL-Datensatz-1-final.csv",
    "§§",
    None,
    Some(DeNoPaDataSetSetting.BaseLine)
  )
) {
  override protected def getColumnNames(lineIterator: Iterator[String]) =
    "Line_Nr" :: super.getColumnNames(lineIterator)
}

object ImportDeNoPaBaseline extends GuiceBuilderRunnable[ImportDeNoPaBaseline] with App { run }