package models

import play.api.libs.json.Json

object ConditionType extends Enumeration {
  val Equals = Value("=")
  val RegexEquals = Value("like")
  val NotEquals = Value("!=")
  val In = Value("in")
  val Greater = Value(">")
  val Less = Value("<")
}

abstract class Criterion {
  val fieldName: String
}

case class EqualsCriterion(fieldName: String, value: String) extends Criterion
case class RegexEqualsCriterion(fieldName: String, value: String) extends Criterion
case class NotEqualsCriterion(fieldName: String, value: String) extends Criterion
case class InCriterion(fieldName: String, value: Seq[String]) extends Criterion
case class GreaterCriterion(fieldName: String, value: Double) extends Criterion
case class LessCriterion(fieldName: String, value: Double) extends Criterion

object Criterion {
  implicit class CriterionInfix(val fieldName: String) {

    def #=(that: String) = EqualsCriterion(fieldName, that)
    def #~(that: String) = RegexEqualsCriterion(fieldName, that)
    def #!=(that: String) = NotEqualsCriterion(fieldName, that)
    def #->(that: Seq[String]) = InCriterion(fieldName, that)
    def #>(that: Double) = GreaterCriterion(fieldName, that)
    def #<(that: Double) = LessCriterion(fieldName, that)
  }
  implicit val ConditionTypeFormat = EnumFormat.enumFormat(ConditionType)
//  implicit val CriterionFormat = Json.format[Criterion]
}
