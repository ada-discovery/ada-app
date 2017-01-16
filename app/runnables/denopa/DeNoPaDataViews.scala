package runnables.denopa

import models.DataView

object DeNoPaDataViews {

  val RawClinicalBaseline = DataView.applyMain(
    Seq("Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"),
    Seq("Geschlecht", "a_Gruppe", "b_Gruppe", "a_Alter"),
    3,
    false
  )

  val RawClinicalFirstVisit = DataView.applyMain(
    Seq("Probanden_Nr", "Geb_Datum", "b_Gruppe"),
    Seq("Geschlecht", "b_Gruppe", "a_Alter"),
    3,
    false
  )

  val RawClinicalSecondVisit = DataView.applyMain(
    Seq("Probanden_Nr", "Geburtsdatum", "c_group"),
    Seq("Geschlecht", "c_group", "c_Alter"),
    3,
    false
  )

  val ClinicalBaseline = DataView.applyMain(
    Seq("Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"),
    Seq("Geschlecht", "a_Gruppe", "b_Gruppe", "a_Alter"),
    3,
    false
  )

  val ClinicalFirstVisit = DataView.applyMain(
    Seq("Probanden_Nr", "Geb_Datum", "b_Gruppe"),
    Seq("Geschlecht", "b_Gruppe", "a_Alter"),
    3,
    false
  )

  val ClinicalSecondVisit = DataView.applyMain(
    Seq("Probanden_Nr", "Geburtsdatum", "c_group"),
    Seq("Geschlecht", "c_group", "c_Alter"),
    3,
    false
  )
}