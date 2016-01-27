package util

import models.EnumFormat
import play.api.libs.json.{JsObject, Json}

case class FilterSpec(conditions : Seq[FilterCondition]) {
  def this() = this(Seq())

  import ConditionType._

  def toCriteria = JsObject(
    conditions.map{ case FilterCondition(fieldName, conditionType, condition) =>
      conditionType match {
        case Equals => (fieldName, Json.toJson(condition))
        case RegexEquals => (fieldName, Json.obj("$regex" -> condition, "$options" -> "i"))
//        case RegexEquals => (fieldName, Json.obj("$regex" -> (condition + ".*"), "$options" -> "i"))
        case Greater => (fieldName, Json.obj("$gt" -> Json.toJson(condition)))
        case Less => (fieldName, Json.obj("$lt" -> Json.toJson(condition)))
      }
    }
  )
}

object ConditionType extends Enumeration {
  val Equals = Value("=")
  val RegexEquals = Value("LIKE")
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