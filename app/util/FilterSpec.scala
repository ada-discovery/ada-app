package util

import models.EnumFormat
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsString, JsObject, Json}
import JsonUtil.toJsonNumber

case class FilterSpec(conditions : Seq[FilterCondition]) {
  def this() = this(Seq())

  import ConditionType._

  def toJsonCriteria = if (conditions.nonEmpty)
    Some(JsObject(
      conditions.map{ case FilterCondition(fieldName, conditionType, condition) =>
        (fieldName, toJsonCondition(conditionType, condition))
      }
    ))
  else
    None

  private def toJsonCondition(conditionType : ConditionType.Value, condition : String) =
    conditionType match {
      case Equals => Json.toJson(condition)
      case RegexEquals => Json.obj("$regex" -> condition, "$options" -> "i")
      case In => {
        val inValues = condition.split(",").map(s => Json.toJson(s.trim) : JsValueWrapper)
        Json.obj("$in" -> Json.arr(inValues : _*))
      }
      case Greater => Json.obj("$gt" -> toJsonNumber(condition))
      case Less => Json.obj("$lt" -> toJsonNumber(condition))
    }
}

object ConditionType extends Enumeration {
  val Equals = Value("=")
  val RegexEquals = Value("like")
  val In = Value("in")
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
  implicit def FilterSpecQueryStringBinder = JsonUtil.createQueryStringBinder[FilterSpec]
}