package runnables.denopa

import javax.inject.Inject
import models.{CsvDataSetImportInfo, DataSetSetting}
import play.api.Configuration
import runnables.DataSetId._

class DeNoPaDataSetImports @Inject() (configuration: Configuration) {

  val list = Seq(
    CsvDataSetImportInfo(
      None,
      "DeNoPa",
      denopa_raw_clinical_baseline,
      "Clinical Baseline",
      Some(configuration.getString("denopa.import.folder").get + "DeNoPa-v1_with_§§.csv"),
      "§§",
      None,
      None,
      false,
      None,
      Some(DeNoPaDataSetSettings.RawClinicalBaseline)
    ),

    CsvDataSetImportInfo(
      None,
      "DeNoPa",
      denopa_raw_clinical_first_visit,
      "Clinical First Visit",
      Some(configuration.getString("denopa.import.folder").get + "DeNoPa-v2_with_§§.csv"),
      "§§",
      None,
      None,
      false,
      None,
      Some(DeNoPaDataSetSettings.RawClinicalFirstVisit)
    ),

    CsvDataSetImportInfo (
      None,
      "DeNoPa",
      denopa_raw_clinical_second_visit,
      "Clinical Second Visit",
      Some (configuration.getString ("denopa.import.folder").get + "DeNoPa-v3_with_§§_and_§%w.csv"),
      "§§",
      Some("§%w"),
      None,
      false,
      None,
      Some(DeNoPaDataSetSettings.RawClinicalSecondVisit)
    )
  )
}