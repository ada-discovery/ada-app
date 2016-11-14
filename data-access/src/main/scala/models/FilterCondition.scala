package models

import java.util.Date

import dataaccess.{BSONObjectIdentity, EnumFormat, User}
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
)

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
  name: String,
  timeCreated: Date,
  conditions: Seq[FilterCondition],
  createdById: Option[BSONObjectID],
  var createdBy: Option[User] = None
)

object FilterShowFieldStyle extends Enumeration {
  var NamesOnly, LabelsOnly, LabelsAndNamesOnlyIfLabelUndefined, NamesAndLabels = Value
}

object FilterCondition {
  implicit val conditionTypeFormat = EnumFormat.enumFormat(ConditionType)
  implicit val filterConditionFormat = Json.format[FilterCondition]
  implicit val filterFormat: Format[Filter] = (
    (__ \ "_id").formatNullable[BSONObjectID] and
    (__ \ "name").format[String] and
    (__ \ "timeCreated").format[Date] and
    (__ \ "conditions").format[Seq[FilterCondition]] and
    (__ \ "createdById").formatNullable[BSONObjectID]
  )(
    Filter(_, _, _, _, _),
    (item: Filter) =>  (item._id, item.name, item.timeCreated, item.conditions, item.createdById)
  )

  implicit object FilterIdentity extends BSONObjectIdentity[Filter] {
    def of(entity: Filter): Option[BSONObjectID] = entity._id
    protected def set(entity: Filter, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}