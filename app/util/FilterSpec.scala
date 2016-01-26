package util

import models.EnumFormat
import play.api.libs.json.Json

case class FilterSpec(conditions : Seq[FilterCondition])

object ConditionType extends Enumeration {
  val Equals = Value("=")
  val Greater = Value(">")
  val Less = Value("<")
}

case class FilterCondition(fieldName: String, conditionType: ConditionType.Value, condition: String) {
  override def toString() = fieldName + " " + conditionType + " " + condition
}

object FilterSpec {
  implicit val ConditionTypeFormat = EnumFormat.enumFormat(ConditionType)
  implicit val FilterConditionFormat = Json.format[FilterCondition]
  implicit val FilterSpecFormat = Json.format[FilterSpec]
}