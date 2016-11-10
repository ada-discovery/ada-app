package runnables.denopa

import dataaccess.DataSetSetting
import runnables.DataSetId._

object DeNoPaDataSetSettings {

  val RawClinicalBaseline = DataSetSetting.apply2(
    None,
    denopa_raw_clinical_baseline,
    "Probanden_Nr",
    Some("_id"),
    Seq("Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"),
    Seq("Geschlecht", "a_Gruppe", "b_Gruppe", "a_Alter"),
    3,
    "a_Alter",
    "a_AESD_I_mean",
    "a_Alter",
    "a_Datum_Aufnahme",
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    false
  )

  val RawClinicalFirstVisit = DataSetSetting.apply2(
    None,
    denopa_raw_clinical_first_visit,
    "Probanden_Nr",
    Some("_id"),
    Seq("Probanden_Nr", "Geb_Datum", "b_Gruppe"),
    Seq("Geschlecht", "b_Gruppe", "a_Alter"),
    3,
    "a_Alter",
    "b_AESD_I_mean",
    "a_Alter",
    "b_Datum_Aufnahme",
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    false
  )

  val RawClinicalSecondVisit = DataSetSetting.apply2(
    None,
    denopa_raw_clinical_second_visit,
    "Probanden_Nr",
    Some("_id"),
    Seq("Probanden_Nr", "Geburtsdatum", "c_group"),
    Seq("Geschlecht", "c_group", "c_Alter"),
    3,
    "c_Alter",
    "c_AESD_I_mean",
    "c_Alter",
    "c_Datum_Aufnahme",
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    false
  )

  val ClinicalBaseline = DataSetSetting.apply2(
    None,
    denopa_clinical_baseline,
    "Probanden_Nr",
    Some("_id"),
    Seq("Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"),
    Seq("Geschlecht", "a_Gruppe", "b_Gruppe", "a_Alter"),
    3,
    "a_Alter",
    "a_AESD_I_mean",
    "a_Alter",
    "a_Datum_Aufnahme",
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    false
  )

  val ClinicalFirstVisit = DataSetSetting.apply2(
    None,
    denopa_clinical_first_visit,
    "Probanden_Nr",
    Some("_id"),
    Seq("Probanden_Nr", "Geb_Datum", "b_Gruppe"),
    Seq("Geschlecht", "b_Gruppe", "a_Alter"),
    3,
    "a_Alter",
    "b_AESD_I_mean",
    "a_Alter",
    "b_Datum_Aufnahme",
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    false
  )

  val ClinicalSecondVisit = DataSetSetting.apply2(
    None,
    denopa_clinical_second_visit,
    "Probanden_Nr",
    Some("_id"),
    Seq("Probanden_Nr", "Geburtsdatum", "c_group"),
    Seq("Geschlecht", "c_group", "c_Alter"),
    3,
    "c_Alter",
    "c_AESD_I_mean",
    "c_Alter",
    "c_Datum_Aufnahme",
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    false
  )
}