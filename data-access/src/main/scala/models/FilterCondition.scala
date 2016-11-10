package models

import dataaccess.EnumFormat
import play.api.libs.json.Json

case class FilterCondition(
  fieldName: String,
  fieldLabel: Option[String],
  conditionType: ConditionType.Value,
  value: String,
  valueLabel: Option[String]
)

object ConditionType extends Enumeration {
  val Equals = Value("=")
  val RegexEquals = Value("like")
  val NotEquals = Value("!=")
  val In = Value("in")
  val NotIn = Value("nin")
  val Greater = Value(">")
  val Less = Value("<")
}

object FilterShowFieldStyle extends Enumeration {
  var NamesOnly, LabelsOnly, LabelsAndNamesOnlyIfLabelUndefined, NamesAndLabels = Value
}

object FilterCondition {
  implicit val ConditionTypeFormat = EnumFormat.enumFormat(ConditionType)
  implicit val FilterConditionFormat = Json.format[FilterCondition]
}