package runnables.denopa

import javax.inject.Inject

import models.DataSetId._
import play.api.Configuration
import runnables.{ImportDataSet, GuiceBuilderRunnable}

class ImportDeNoPaFirstVisit @Inject() (configuration: Configuration) extends ImportDataSet(
  "DeNoPa",
  denopa_firstvisit,
  "DeNoPa First Visit",
  Some(DeNoPaDataSetSetting.FirstVisit),
  configuration.getString("denopa.import.folder").get,
  "Denopa-V2-FU1-Datensatz-final.csv",
  "§§"
) {
  override protected def getColumnNames(lineIterator: Iterator[String]) =
    "Line_Nr" :: super.getColumnNames(lineIterator)
}

object ImportDeNoPaFirstVisit extends GuiceBuilderRunnable[ImportDeNoPaFirstVisit] with App { run }
