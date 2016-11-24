package runnables.denopa

import models.DataView

object DeNoPaDataViews {

  val RawClinicalBaseline = DataView.applyMain(
    Seq("Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"),
    Seq("Geschlecht", "a_Gruppe", "b_Gruppe", "a_Alter"),
    3
  )

  val RawClinicalFirstVisit = DataView.applyMain(
    Seq("Probanden_Nr", "Geb_Datum", "b_Gruppe"),
    Seq("Geschlecht", "b_Gruppe", "a_Alter"),
    3
  )

  val RawClinicalSecondVisit = DataView.applyMain(
    Seq("Probanden_Nr", "Geburtsdatum", "c_group"),
    Seq("Geschlecht", "c_group", "c_Alter"),
    3
  )

  val ClinicalBaseline = DataView.applyMain(
    Seq("Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"),
    Seq("Geschlecht", "a_Gruppe", "b_Gruppe", "a_Alter"),
    3
  )

  val ClinicalFirstVisit = DataView.applyMain(
    Seq("Probanden_Nr", "Geb_Datum", "b_Gruppe"),
    Seq("Geschlecht", "b_Gruppe", "a_Alter"),
    3
  )

  val ClinicalSecondVisit = DataView.applyMain(
    Seq("Probanden_Nr", "Geburtsdatum", "c_group"),
    Seq("Geschlecht", "c_group", "c_Alter"),
    3
  )
}