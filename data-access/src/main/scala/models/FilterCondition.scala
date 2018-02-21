package models

import java.util.Date

import dataaccess._
import models.json.{EitherFormat, EnumFormat, OptionFormat}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

case class FilterCondition(
  fieldName: String,
  fieldLabel: Option[String],
  conditionType: ConditionType.Value,
  value: Option[String],
  valueLabel: Option[String]
) {
  def fieldLabelOrElseName = fieldLabel.getOrElse(fieldName)
}

object ConditionType extends Enumeration {
  val Equals = Value("=")
  val RegexEquals = Value("like")
  val NotEquals = Value("!=")
  val In = Value("in")
  val NotIn = Value("nin")
  val Greater = Value(">")
  val GreaterEqual = Value(">=")
  val Less = Value("<")
  val LessEqual = Value("<=")
}

case class Filter(
  _id: Option[BSONObjectID],
  name: Option[String],
  conditions: Seq[FilterCondition],
  isPrivate: Boolean = false,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Option[Date] = Some(new Date()),
  var createdBy: Option[User] = None
) {
  def this(conditions: Seq[FilterCondition]) =
    this(None, None, conditions)

  def conditionsOrId: Either[Seq[FilterCondition], BSONObjectID] =
    _id.fold(Left(conditions): Either[Seq[FilterCondition], BSONObjectID]){filterId => Right(filterId)}
}

object FilterShowFieldStyle extends Enumeration {
  var NamesOnly, LabelsOnly, LabelsAndNamesOnlyIfLabelUndefined, NamesAndLabels = Value
}

object FilterCondition {
  implicit val conditionTypeFormat = EnumFormat.enumFormat(ConditionType)

  implicit val filterConditionFormat = Json.format[FilterCondition]

  implicit val filterFormat: Format[Filter] = (
    (__ \ "_id").formatNullable[BSONObjectID] and
    (__ \ "name").formatNullable[String] and
    (__ \ "conditions").format[Seq[FilterCondition]] and
    (__ \ "isPrivate").format[Boolean] and
    (__ \ "createdById").formatNullable[BSONObjectID] and
    (__ \ "timeCreated").formatNullable[Date]
  )(
    Filter(_, _, _, _, _, _),
    (item: Filter) =>  (item._id, item.name, item.conditions, item.isPrivate, item.createdById, item.timeCreated)
  )

  implicit object FilterIdentity extends BSONObjectIdentity[Filter] {
    def of(entity: Filter): Option[BSONObjectID] = entity._id
    protected def set(entity: Filter, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  type FilterOrId = Either[Seq[FilterCondition], BSONObjectID]

  implicit val eitherFilterOrIdFormat = EitherFormat[Seq[FilterCondition], BSONObjectID]

  def filterOrIdsToJson(filterOrIds: Seq[FilterOrId]): JsValue = {
    Json.toJson(
      filterOrIds.map(inputFilterConditionOrId => Json.toJson(inputFilterConditionOrId))
    )
  }

  import ConditionType._

  def toCriterion(
    valueConverters: Map[String, String => Option[Any]])(
    filterCondition: FilterCondition
  ): Option[Criterion[Any]] = {
    val fieldName = filterCondition.fieldName

    // convert values if any converters provided
    def convertValue(text: Option[String]): Option[Any] = text.flatMap( text =>
      valueConverters.get(fieldName).map(converter =>
        converter.apply(text.trim)
      ).getOrElse(Some(text.trim)) // if no converter found use a provided string value
    )

    val value =  filterCondition.value

    def convertedValue = convertValue(value)
    def convertedValues: Seq[Any] = {
      value.map(_.split(",").toSeq.flatMap(x => convertValue(Some(x)))).getOrElse(Nil)
    }

    filterCondition.conditionType match {
      case Equals => Some(
        convertedValue.map(
          EqualsCriterion(fieldName, _)
        ).getOrElse(
          EqualsNullCriterion(fieldName)
        )
      )

      case RegexEquals => Some(RegexEqualsCriterion(fieldName, value.getOrElse("")))            // string expected

      case NotEquals => Some(
        convertedValue.map(
          NotEqualsCriterion(fieldName, _)
        ).getOrElse(
          NotEqualsNullCriterion(fieldName)
        )
      )

      case In => Some(InCriterion(fieldName, convertedValues))

      case NotIn => Some(NotInCriterion(fieldName, convertedValues))

      case Greater => convertedValue.map(GreaterCriterion(fieldName, _))

      case GreaterEqual => convertedValue.map(GreaterEqualCriterion(fieldName, _))

      case Less => convertedValue.map(LessCriterion(fieldName, _))

      case LessEqual => convertedValue.map(LessEqualCriterion(fieldName, _))
    }
  }
}