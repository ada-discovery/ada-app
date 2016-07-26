package util

import models.EnumFormat
import play.api.libs.json.Json

case class FilterCondition(fieldName: String, conditionType: ConditionType.Value, value: String)

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
  implicit def FilterSpecQueryStringBinder = JsonUtil.createQueryStringBinder[Seq[FilterCondition]]
}