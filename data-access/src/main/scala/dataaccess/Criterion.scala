package dataaccess

sealed abstract class Criterion[+T] {
  val fieldName: String
  val value: T
  def copyWithFieldName(fieldName: String): Criterion[T]
}

case class EqualsCriterion[T](fieldName: String, value: T) extends Criterion[T] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class EqualsNullCriterion(fieldName: String) extends Criterion[Unit] {
  override val value = ()
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class RegexEqualsCriterion(fieldName: String, value: String) extends Criterion[String] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class NotEqualsCriterion[T](fieldName: String, value: T) extends Criterion[T] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class NotEqualsNullCriterion(fieldName: String) extends Criterion[Unit] {
  override val value = ()
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class InCriterion[V](fieldName: String, value: Seq[V]) extends Criterion[Seq[V]] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class NotInCriterion[V](fieldName: String, value: Seq[V]) extends Criterion[Seq[V]] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class GreaterCriterion[T](fieldName: String, value: T) extends Criterion[T] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class GreaterEqualCriterion[T](fieldName: String, value: T) extends Criterion[T] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class LessCriterion[T](fieldName: String, value: T) extends Criterion[T] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}
case class LessEqualCriterion[T](fieldName: String, value: T) extends Criterion[T] {
  override def copyWithFieldName(fieldName: String) = copy(fieldName = fieldName)
}

object Criterion {
  implicit class Infix(val fieldName: String) extends AnyVal {

    def #==[T](value: T) = EqualsCriterion(fieldName, value)
    def #=@[T] = EqualsNullCriterion(fieldName)

    def #!=[T](value: T) = NotEqualsCriterion(fieldName, value)
    def #!@[T] = NotEqualsNullCriterion(fieldName)

    def #~(value: String) = RegexEqualsCriterion(fieldName, value)

    def #->[V](value: Seq[V]) = InCriterion(fieldName, value)
    def #!->[V](value: Seq[V]) = NotInCriterion(fieldName, value)

    def #>[T](value: T) = GreaterCriterion(fieldName, value)
    def #>=[T](value: T) = GreaterEqualCriterion(fieldName, value)

    def #<[T](value: T) = LessCriterion(fieldName, value)
    def #<=[T](value: T) = LessEqualCriterion(fieldName, value)
  }

  def matches[T](
    criterion: Criterion[T],
    value: T
  ): Boolean =
    criterion match {
      case c: EqualsCriterion[T] => c.value.equals(value)

      case c: EqualsNullCriterion => value == null || value == None

      case c: RegexEqualsCriterion => value.equals(c.value) // TODO

      case c: NotEqualsCriterion[T] => !c.value.equals(value)

      case c: NotEqualsNullCriterion => value != null && value != None

      case c: InCriterion[T] => c.value.contains(value)

      case c: NotInCriterion[T] => !c.value.contains(value)

      case c: GreaterCriterion[T] => value.toString.toDouble > c.value.toString.toDouble // TODO

      case c: GreaterEqualCriterion[T] => value.toString.toDouble >= c.value.toString.toDouble // TODO

      case c: LessCriterion[T] => value.toString.toDouble < c.value.toString.toDouble // TODO

      case c: LessEqualCriterion[T] => value.toString.toDouble <= c.value.toString.toDouble // TODO
    }
}