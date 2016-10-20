package services

import java.text.SimpleDateFormat

object DeNoPaSetting {

  val textBooleanValues = List("false", "true", "nein", "ja", "richtig", "falsch")
  val textBooleanMap = Map("false" -> false, "true" -> true, "nein" -> false, "ja" -> true, "falsch" -> false, "richtig" -> true)
  val dateFormats = List("yyyy-MM-dd", "dd.MM.yyyy", "MM.yyyy")
  val storeDateFormat =  new SimpleDateFormat("dd.MM.yyyy")
}
