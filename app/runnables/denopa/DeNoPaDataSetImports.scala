package runnables.denopa

import javax.inject.Inject
import models.CsvDataSetImport
import play.api.Configuration
import runnables.DataSetId._

class DeNoPaDataSetImports @Inject() (configuration: Configuration) {

  val list = Seq(
    CsvDataSetImport(
      None,
      "DeNoPa",
      denopa_raw_clinical_baseline,
      "Raw Clinical Baseline",
      Some(configuration.getString("denopa.import.folder").get + "DeNoPa-v1_with_§§.csv"),
      "§§",
      None,
      None,
      true,
      false,
      None,
      None,
      None,
      false,
      None,
      false,
      None,
      Some(DeNoPaDataSetSettings.RawClinicalBaseline),
      Some(DeNoPaDataViews.RawClinicalBaseline)
    ),

    CsvDataSetImport(
      None,
      "DeNoPa",
      denopa_raw_clinical_first_visit,
      "Raw Clinical First Visit",
      Some(configuration.getString("denopa.import.folder").get + "DeNoPa-v2_with_§§.csv"),
      "§§",
      None,
      None,
      true,
      false,
      None,
      None,
      None,
      false,
      None,
      false,
      None,
      Some(DeNoPaDataSetSettings.RawClinicalFirstVisit),
      Some(DeNoPaDataViews.RawClinicalFirstVisit)
    ),

    CsvDataSetImport (
      None,
      "DeNoPa",
      denopa_raw_clinical_second_visit,
      "Raw Clinical Second Visit",
      Some (configuration.getString ("denopa.import.folder").get + "DeNoPa-v3_with_§§_and_§%w.csv"),
      "§§",
      Some("§%w"),
      None,
      true,
      false,
      None,
      None,
      None,
      false,
      None,
      false,
      None,
      Some(DeNoPaDataSetSettings.RawClinicalSecondVisit),
      Some(DeNoPaDataViews.RawClinicalSecondVisit)
    )
  )
}