package dataaccess

import dataaccess.{FieldTypeInferrer, FieldTypeFactory}

object FieldTypeHelper {
  val nullAliases = Seq("", "na", "n/a", "null")
  val dateFormats = Seq(
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd HH:mm",
    "yyyy-MM-dd HH",
    "yyyy-MM-dd",
    "dd-MMM-yyyy HH:mm:ss",
    "dd-MMM-yyyy HH:mm",
    "dd-MMM-yyyy HH",
    "dd-MMM-yyyy",
    "dd.MMM.yyyy HH:mm:ss",
    "dd.MMM.yyyy HH:mm",
    "dd.MMM.yyyy HH",
    "dd.MMM.yyyy",
    "dd.MM.yyyy HH:mm:ss",
    "dd.MM.yyyy HH:mm",
    "dd.MM.yyyy HH",
    "dd.MM.yyyy",
    "MM.yyyy"
  )
  val displayDateFormat = "yyyy-MM-dd HH:mm:ss"
  val enumValuesMaxCount = 20

  val fieldTypeFactory = FieldTypeFactory(nullAliases.toSet, dateFormats, displayDateFormat, enumValuesMaxCount)
  val fieldTypeInferrer = FieldTypeInferrer(nullAliases.toSet, dateFormats, displayDateFormat, enumValuesMaxCount)
}