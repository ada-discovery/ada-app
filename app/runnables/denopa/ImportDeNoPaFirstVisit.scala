package runnables.denopa

import javax.inject.Inject

import models.DataSetId._
import play.api.Configuration
import runnables.{DataSetImportInfo, ImportDataSet, GuiceBuilderRunnable}

class ImportDeNoPaFirstVisit @Inject() (configuration: Configuration) extends ImportDataSet(
  DataSetImportInfo(
    "DeNoPa",
    denopa_firstvisit,
    "DeNoPa First Visit",
    configuration.getString("denopa.import.folder").get + "Denopa-V2-FU1-Datensatz-final.csv",
    "§§",
    None,
    Some(DeNoPaDataSetSetting.FirstVisit)
  )
) {
  override protected def getColumnNames(lineIterator: Iterator[String]) =
    "Line_Nr" :: super.getColumnNames(lineIterator)
}

object ImportDeNoPaFirstVisit extends GuiceBuilderRunnable[ImportDeNoPaFirstVisit] with App { run }