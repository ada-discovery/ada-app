package services

import dataaccess.{FieldTypeInferrer, FieldTypeFactory}

object FieldTypeHelper {
  val nullAliases = Seq("", "na", "n/a")
  val dateFormats = Seq(
    "yyyy-MM-dd",
    "yyyy-MM-dd HH:mm",
    "yyyy-MM-dd HH:mm:ss",
    "dd-MMM-yyyy HH:mm:ss",
    "dd.MM.yyyy",
    "MM.yyyy"
  )
  val enumValuesMaxCount = 20

  val fieldTypeFactory = FieldTypeFactory(nullAliases.toSet, dateFormats, enumValuesMaxCount)
  val fieldTypeInferrer = FieldTypeInferrer(nullAliases.toSet, dateFormats, enumValuesMaxCount)
}