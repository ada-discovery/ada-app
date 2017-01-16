package runnables.denopa

import models.{StorageType, DataSetSetting}
import runnables.DataSetId._

object DeNoPaDataSetSettings {

  val RawClinicalBaseline = DataSetSetting(
    None,
    denopa_raw_clinical_baseline,
    "Probanden_Nr",
    Some("_id"),
    "a_Alter",
    "a_AESD_I_mean",
    "a_Alter",
    "a_Datum_Aufnahme",
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val RawClinicalFirstVisit = DataSetSetting(
    None,
    denopa_raw_clinical_first_visit,
    "Probanden_Nr",
    Some("_id"),
    "a_Alter",
    "b_AESD_I_mean",
    "a_Alter",
    "b_Datum_Aufnahme",
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val RawClinicalSecondVisit = DataSetSetting(
    None,
    denopa_raw_clinical_second_visit,
    "Probanden_Nr",
    Some("_id"),
    "c_Alter",
    "c_AESD_I_mean",
    "c_Alter",
    "c_Datum_Aufnahme",
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val ClinicalBaseline = DataSetSetting(
    None,
    denopa_clinical_baseline,
    "Probanden_Nr",
    Some("_id"),
    "a_Alter",
    "a_AESD_I_mean",
    "a_Alter",
    "a_Datum_Aufnahme",
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val ClinicalFirstVisit = DataSetSetting(
    None,
    denopa_clinical_first_visit,
    "Probanden_Nr",
    Some("_id"),
    "a_Alter",
    "b_AESD_I_mean",
    "a_Alter",
    "b_Datum_Aufnahme",
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val ClinicalSecondVisit = DataSetSetting(
    None,
    denopa_clinical_second_visit,
    "Probanden_Nr",
    Some("_id"),
    "c_Alter",
    "c_AESD_I_mean",
    "c_Alter",
    "c_Datum_Aufnahme",
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )
}