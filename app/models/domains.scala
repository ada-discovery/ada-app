package models

import dataaccess.{FieldTypeId, EnumFormat, BSONObjectIdentity}
import models.FilterCondition.FilterConditionFormat
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import java.util.{UUID, Date}
import play.modules.reactivemongo.json.BSONFormats._
import util.JsonUtil

case class Message(
  _id: Option[BSONObjectID],
  content: String,
  createdByUser: Option[String] = None, // no user means a system message
  isUserAdmin: Boolean = false,
  timeCreated: Date = new Date()
)

object Message {
  implicit val MessageFormat = Json.format[Message]

  implicit object MessageIdentity extends BSONObjectIdentity[Message] {
    def of(entity: Message): Option[BSONObjectID] = entity._id
    protected def set(entity: Message, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}

case class Translation(_id : Option[BSONObjectID], original : String, translated : String) {
  override def toString = original + " -> " + translated
}

object Translation {
  implicit val TranslationFormat = Json.format[Translation]

  implicit object TranslationIdentity extends BSONObjectIdentity[Translation] {
    def of(entity: Translation): Option[BSONObjectID] = entity._id
    protected def set(entity: Translation, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}

object QueryStringBinders {
  implicit val FilterSpecQueryStringBinder = JsonUtil.createQueryStringBinder[Seq[FilterCondition]]
  implicit val FieldTypeIdFormat = EnumFormat.enumFormat(FieldTypeId)
  implicit val FieldTypeIdsQueryStringBinder = JsonUtil.createQueryStringBinder[Seq[FieldTypeId.Value]]
}