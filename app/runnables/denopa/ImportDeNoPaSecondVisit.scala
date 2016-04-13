package runnables.denopa

import javax.inject.Inject

import models.DataSetId._
import play.api.Configuration
import runnables.{ImportDataSet, GuiceBuilderRunnable}

class ImportDeNoPaSecondVisit @Inject() (configuration: Configuration) extends ImportDataSet(
  denopa_secondvisit,
  "DeNoPa Second Visit",
  Some(DeNoPaDataSetSetting.SecondVisit),
  configuration.getString("denopa.import.folder").get,
  "DeNoPa.v3.sav_with_§§_and_§%w.csv",
  "§§",
  Some("§%w")
) {
  override protected def getColumnNames(lineIterator: Iterator[String]) =
    "Line_Nr" :: super.getColumnNames(lineIterator)
}

object ImportDeNoPaSecondVisit extends GuiceBuilderRunnable[ImportDeNoPaSecondVisit] with App { run }