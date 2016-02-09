package services

import java.text.SimpleDateFormat

import util.TypeInferenceProvider

object DeNoPaSetting {

  val nullAliases = List("na", "")
  val textBooleanValues = List("false", "true", "nein", "ja", "richtig", "falsch")
  val numBooleanValues = List("0", "1")

  val textBooleanMap = Map("false" -> false, "true" -> true, "nein" -> false, "ja" -> true, "falsch" -> false, "richtig" -> true)
  val numBooleanMap = Map("0" -> false, "1" -> true)
  val dateFormats = List("yyyy-MM-dd", "dd.MM.yyyy", "MM.yyyy")
  val storeDateFormat =  new SimpleDateFormat("dd.MM.yyyy")

  val enumValuesThreshold = 20
  val enumFrequencyThreshold = 0.02

  val typeInferenceProvider = TypeInferenceProvider(nullAliases, textBooleanValues, numBooleanValues, dateFormats, enumValuesThreshold, enumFrequencyThreshold)
}
