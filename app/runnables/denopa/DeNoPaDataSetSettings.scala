package runnables.denopa

import models.DataSetSetting
import runnables.DataSetId._

object DeNoPaDataSetSettings {

  val RawClinicalBaseline = DataSetSetting.apply2(
    None,
    denopa_raw_clinical_baseline,
    "Probanden_Nr",
    "Line_Nr",
    Seq("Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"),
    Seq("Geschlecht", "a_Gruppe", "b_Gruppe", "a_Alter"),
    3,
    "a_Alter",
    "a_AESD_I_mean",
    "a_Alter",
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val RawClinicalFirstVisit = DataSetSetting.apply2(
    None,
    denopa_raw_clinical_first_visit,
    "Probanden_Nr",
    "Line_Nr",
    Seq("Probanden_Nr", "Geb_Datum", "b_Gruppe"),
    Seq("Geschlecht", "b_Gruppe", "a_Alter"),
    3,
    "a_Alter",
    "b_AESD_I_mean",
    "a_Alter",
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val RawClinicalSecondVisit = DataSetSetting.apply2(
    None,
    denopa_raw_clinical_second_visit,
    "Probanden_Nr",
    "cdisc_dm_usubjd",
    Seq("Probanden_Nr", "Geburtsdatum", "c_group"),
    Seq("Geschlecht", "c_group", "c_Alter"),
    3,
    "c_Alter",
    "c_AESD_I_mean",
    "c_Alter",
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val ClinicalBaseline = DataSetSetting.apply2(
    None,
    denopa_clinical_baseline,
    "Probanden_Nr",
    "Line_Nr",
    Seq("Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"),
    Seq("Geschlecht", "a_Gruppe", "b_Gruppe", "a_Alter"),
    3,
    "a_Alter",
    "a_AESD_I_mean",
    "a_Alter",
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val ClinicalFirstVisit = DataSetSetting.apply2(
    None,
    denopa_clinical_first_visit,
    "Probanden_Nr",
    "Line_Nr",
    Seq("Probanden_Nr", "Geb_Datum", "b_Gruppe"),
    Seq("Geschlecht", "b_Gruppe", "a_Alter"),
    3,
    "a_Alter",
    "b_AESD_I_mean",
    "a_Alter",
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val ClinicalSecondVisit = DataSetSetting.apply2(
    None,
    denopa_clinical_second_visit,
    "Probanden_Nr",
    "Line_Nr",
    Seq("Probanden_Nr", "Geburtsdatum", "c_group"),
    Seq("Geschlecht", "c_group", "c_Alter"),
    3,
    "c_Alter",
    "c_AESD_I_mean",
    "c_Alter",
    None,
    Map(("\r", " "), ("\n", " "))
  )
}