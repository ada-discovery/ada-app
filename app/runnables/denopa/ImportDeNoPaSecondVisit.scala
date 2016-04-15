package runnables.denopa

import javax.inject.Inject

import models.DataSetId._
import play.api.Configuration
import runnables.{DataSetImportInfo, ImportDataSet, GuiceBuilderRunnable}

class ImportDeNoPaSecondVisit @Inject() (configuration: Configuration) extends ImportDataSet(
  DataSetImportInfo(
    "DeNoPa",
    denopa_secondvisit,
    "DeNoPa Second Visit",
    configuration.getString("denopa.import.folder").get + "DeNoPa.v3.sav_with_§§_and_§%w.csv",
    "§§",
    Some("§%w"),
    Some(DeNoPaDataSetSetting.SecondVisit)
  )
)

object ImportDeNoPaSecondVisit extends GuiceBuilderRunnable[ImportDeNoPaSecondVisit] with App { run }