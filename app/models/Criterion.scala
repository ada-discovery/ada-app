package models

import play.api.libs.json.Format

sealed abstract class Criterion[+T] {
  val fieldName: String
  val value: T
  def copyWithFieldName(fieldName: String): Criterion[T]
}

case class EqualsCriterion[T](fieldName: String, value: T)(implicit val valueFormat: Format[T]) extends Criterion[T] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class RegexEqualsCriterion(fieldName: String, value: String) extends Criterion[String] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class NotEqualsCriterion[T](fieldName: String, value: T)(implicit val valueFormat: Format[T]) extends Criterion[T] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class InCriterion[V](fieldName: String, value: Seq[V])(implicit val elementFormat: Format[V]) extends Criterion[Seq[V]] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class NotInCriterion[V](fieldName: String, value: Seq[V])(implicit val elementFormat: Format[V]) extends Criterion[Seq[V]] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class GreaterCriterion(fieldName: String, value: Double) extends Criterion[Double] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class LessCriterion(fieldName: String, value: Double) extends Criterion[Double] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}

object Criterion {
  implicit class CriterionInfix(val fieldName: String) {

    def #=[T: Format](value: T) = EqualsCriterion(fieldName, value)
    def #~(value: String) = RegexEqualsCriterion(fieldName, value)
    def #!=[T: Format](value: T) = NotEqualsCriterion(fieldName, value)
    def #=>[V: Format](value: Seq[V]) = InCriterion(fieldName, value)
    def #!->(value: Seq[String]) = NotInCriterion(fieldName, value)
    def #>(value: Double) = GreaterCriterion(fieldName, value)
    def #<(value: Double) = LessCriterion(fieldName, value)
  }
}
