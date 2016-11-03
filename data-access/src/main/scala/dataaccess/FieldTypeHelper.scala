package dataaccess

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
  val maxEnumValuesCount = 20
  val minAvgValuesPerEnum = 1.5

  val arrayDelimiter = ","

  val fieldTypeFactory = FieldTypeFactory(nullAliases.toSet, dateFormats, displayDateFormat, arrayDelimiter)
  val fieldTypeInferrerFactory = FieldTypeInferrerFactory(fieldTypeFactory, maxEnumValuesCount, minAvgValuesPerEnum, arrayDelimiter)

  val fieldTypeInferrer = fieldTypeInferrerFactory.apply
  val jsonFieldTypeInferrer = fieldTypeInferrerFactory.applyJson
}