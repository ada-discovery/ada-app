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
      false,
      false,
      None,
      Some(DeNoPaDataSetSettings.RawClinicalBaseline)
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
      false,
      false,
      None,
      Some(DeNoPaDataSetSettings.RawClinicalFirstVisit)
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
      false,
      false,
      None,
      Some(DeNoPaDataSetSettings.RawClinicalSecondVisit)
    )
  )
}