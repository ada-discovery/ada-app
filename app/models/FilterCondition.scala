package models

import dataaccess.{FieldTypeId, EnumFormat}
import play.api.libs.json.Json
import util.JsonUtil

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

object FilterCondition {
  implicit val ConditionTypeFormat = EnumFormat.enumFormat(ConditionType)
  implicit val FilterConditionFormat = Json.format[FilterCondition]
  implicit val FilterSpecQueryStringBinder = JsonUtil.createQueryStringBinder[Seq[FilterCondition]]
  // TODO: move elsewhere
  implicit val FieldTypeIdFormat = EnumFormat.enumFormat(FieldTypeId)
  implicit val FieldTypeIdsQueryStringBinder = JsonUtil.createQueryStringBinder[Seq[FieldTypeId.Value]]
}