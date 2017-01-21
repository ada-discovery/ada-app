package models

import java.util.Date

import dataaccess.{EitherFormat, BSONObjectIdentity, EnumFormat, User}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

case class FilterCondition(
  fieldName: String,
  fieldLabel: Option[String],
  conditionType: ConditionType.Value,
  value: String,
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
  val Less = Value("<")
}

case class Filter(
  _id: Option[BSONObjectID],
  name: Option[String],
  conditions: Seq[FilterCondition],
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
    (__ \ "createdById").formatNullable[BSONObjectID] and
    (__ \ "timeCreated").formatNullable[Date]
  )(
    Filter(_, _, _, _, _),
    (item: Filter) =>  (item._id, item.name, item.conditions, item.createdById, item.timeCreated)
  )

  implicit object FilterIdentity extends BSONObjectIdentity[Filter] {
    def of(entity: Filter): Option[BSONObjectID] = entity._id
    protected def set(entity: Filter, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  def filterOrIdsToJson(filterOrIds: Seq[Either[Seq[FilterCondition], BSONObjectID]]): JsValue = {
    implicit val eitherFormat = EitherFormat[Seq[FilterCondition], BSONObjectID]
    Json.toJson(
      filterOrIds.map(inputFilterConditionOrId => Json.toJson(inputFilterConditionOrId))
    )
  }
}