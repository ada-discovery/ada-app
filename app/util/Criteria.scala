package util

import models.{ConditionType, Criterion, EnumFormat}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsString, JsObject, Json}
import JsonUtil.toJsonNumber

case class FilterCriterion(fieldName: String, conditionType: ConditionType.Value, value: String)

case class Criteria(criteria: Seq[FilterCriterion]) {
  def this() = this(Seq())

  def toJsonCriteria = if (criteria.nonEmpty)
    Some(JsObject(
      criteria.map{ case FilterCriterion(fieldName, conditionType, condition) =>
        (fieldName, toJsonCondition(conditionType, condition))
      }
    ))
  else
    None

  import models.ConditionType._

  private def toJsonCondition(conditionType: ConditionType.Value, value: String) =
    conditionType match {
      case Equals => Json.toJson(value)
      case RegexEquals => Json.obj("$regex" -> value, "$options" -> "i")
      case NotEquals => Json.obj("$ne" -> value)
      case In => {
        val inValues = value.split(",").map(_.trim : JsValueWrapper)
        Json.obj("$in" -> Json.arr(inValues : _*))
      }
      case Greater => Json.obj("$gt" -> toJsonNumber(value))
      case Less => Json.obj("$lt" -> toJsonNumber(value))
    }
}

object Criteria {
  implicit val ConditionTypeFormat = EnumFormat.enumFormat(ConditionType)
  implicit val FilterConditionFormat = Json.format[FilterCriterion]
  implicit val FilterSpecFormat = Json.format[Criteria]
  implicit def FilterSpecQueryStringBinder = JsonUtil.createQueryStringBinder[Criteria]
}