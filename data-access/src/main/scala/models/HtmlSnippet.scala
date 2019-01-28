package models

import reactivemongo.bson.BSONObjectID
import java.util.Date

import dataaccess.BSONObjectIdentity
import models.json.{EnumFormat, SerializableFormat}
import play.api.libs.json.Json
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

object HtmlSnippetId extends Enumeration {
  val Homepage, Contact, Links = Value
}

case class HtmlSnippet(
  _id: Option[BSONObjectID] = None,
  snippetId: HtmlSnippetId.Value,
  content: String,
  active: Boolean = true,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
)

object HtmlSnippet {

  implicit val htmlSnippedIdEnumFormat = EnumFormat(HtmlSnippetId)
  val htmlSnippetFormat = Json.format[HtmlSnippet]
  implicit val serializableHtmlSnippetFormat = new SerializableFormat(htmlSnippetFormat.reads, htmlSnippetFormat.writes)

  implicit object HtmlSnippetIdentity extends BSONObjectIdentity[HtmlSnippet] {
    def of(entity: HtmlSnippet): Option[BSONObjectID] = entity._id
    protected def set(entity: HtmlSnippet, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}