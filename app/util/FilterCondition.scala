package util

import models.{Criterion, EnumFormat}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsObject, Json}
import JsonUtil.toJsonNumber

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

//case class Filter(criteria: Seq[FilterCondition]) {
//  def this() = this(Seq())
//
//  def toJsonCriteria = if (criteria.nonEmpty)
//    Some(JsObject(
//      criteria.map{ case FilterCondition(fieldName, conditionType, condition) =>
//        (fieldName, toJsonCondition(conditionType, condition))
//      }
//    ))
//  else
//    None
//
//  import util.ConditionType._
//
//  private def toJsonCondition(conditionType: ConditionType.Value, value: String) =
//    conditionType match {
//      case Equals => Json.toJson(value)
//      case RegexEquals => Json.obj("$regex" -> value, "$options" -> "i")
//      case NotEquals => Json.obj("$ne" -> value)
//      case In => {
//        val inValues = value.split(",").map(_.trim : JsValueWrapper)
//        Json.obj("$in" -> Json.arr(inValues : _*))
//      }
//      case Greater => Json.obj("$gt" -> toJsonNumber(value))
//      case Less => Json.obj("$lt" -> toJsonNumber(value))
//    }
//}

object FilterCondition {
  implicit val ConditionTypeFormat = EnumFormat.enumFormat(ConditionType)
  implicit val FilterConditionFormat = Json.format[FilterCondition]
  implicit def FilterSpecQueryStringBinder = JsonUtil.createQueryStringBinder[Seq[FilterCondition]]
}