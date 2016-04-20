package runnables.denopa

import models.DataSetSetting
import runnables.DataSetId._

// just temporary, these settings should be provided through the data upload ui
object DeNoPaDataSetSetting {

  val BaseLine = DataSetSetting(
    None,
    denopa_baseline,
    "Probanden_Nr",
    "Line_Nr",
    Seq("Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"),
    Seq("Geschlecht", "a_Gruppe", "b_Gruppe", "a_Alter"),
    "a_Alter",
    "a_AESD_I_mean",
    "a_Alter",
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val FirstVisit = DataSetSetting(
    None,
    denopa_firstvisit,
    "Probanden_Nr",
    "Line_Nr",
    Seq("Probanden_Nr", "Geb_Datum", "b_Gruppe"),
    Seq("Geschlecht", "b_Gruppe", "a_Alter"),
    "a_Alter",
    "b_AESD_I_mean",
    "a_Alter",
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val SecondVisit = DataSetSetting(
    None,
    denopa_secondvisit,
    "Probanden_Nr",
    "cdisc_dm_usubjd",
    Seq("Probanden_Nr", "Geburtsdatum", "c_group"),
    Seq("Geschlecht", "c_group", "c_Alter"),
    "c_Alter",
    "c_AESD_I_mean",
    "c_Alter",
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val CuratedBaseLine = DataSetSetting(
    None,
    denopa_curated_baseline,
    "Probanden_Nr",
    "Line_Nr",
    Seq("Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"),
    Seq("Geschlecht", "a_Gruppe", "b_Gruppe", "a_Alter"),
    "a_Alter",
    "a_AESD_I_mean",
    "a_Alter",
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val CuratedFirstVisit = DataSetSetting(
    None,
    denopa_curated_firstvisit,
    "Probanden_Nr",
    "Line_Nr",
    Seq("Probanden_Nr", "Geb_Datum", "b_Gruppe"),
    Seq("Geschlecht", "b_Gruppe", "a_Alter"),
    "a_Alter",
    "b_AESD_I_mean",
    "a_Alter",
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val CuratedSecondVisit = DataSetSetting(
    None,
    denopa_curated_secondvisit,
    "Probanden_Nr",
    "Line_Nr",
    Seq("Probanden_Nr", "Geburtsdatum", "c_group"),
    Seq("Geschlecht", "c_group", "c_Alter"),
    "c_Alter",
    "c_AESD_I_mean",
    "c_Alter",
    None,
    Map(("\r", " "), ("\n", " "))
  )
}