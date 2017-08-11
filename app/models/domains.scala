package models

import com.fasterxml.jackson.core.JsonParseException
import controllers.BSONObjectIDQueryStringBindable
import play.api.mvc.QueryStringBindable
import util.JsonUtil
import dataaccess.BSONObjectIdentity
import models.FilterCondition.{FilterOrId, filterConditionFormat, filterFormat}
import models.DataSetFormattersAndIds.enumTypeFormat
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import java.util.{Date, UUID}

import reactivemongo.play.json.BSONFormats._
import play.api.libs.json._
import models.json.EnumFormat
import models.json.EitherFormat._

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

  def createQueryStringBinder[E:Format](implicit stringBinder: QueryStringBindable[String]) = new QueryStringBindable[E] {

    override def bind(
      key: String,
      params: Map[String, Seq[String]]
    ): Option[Either[String, E]] = {
      for {
        jsonString <- stringBinder.bind(key, params)
      } yield {
        jsonString match {
          case Right(jsonString) => {
            try {
              val filterJson = Json.parse(jsonString)
              Right(filterJson.as[E])
            } catch {
              case e: JsonParseException => Left("Unable to bind JSON from String to " + key)
            }
          }
          case _ => Left("Unable to bind JSON from String to " + key)
        }
      }
    }

    override def unbind(key: String, filterSpec: E): String =
      stringBinder.unbind(key, Json.stringify(Json.toJson(filterSpec)))
  }

  implicit val FilterConditionQueryStringBinder = createQueryStringBinder[Seq[FilterCondition]]
  implicit val FilterQueryStringBinder = createQueryStringBinder[Filter]
  implicit val FieldTypeIdsQueryStringBinder = createQueryStringBinder[Seq[FieldTypeId.Value]]
  implicit val BSONObjectIDQueryStringBinder = BSONObjectIDQueryStringBindable
  implicit val FilterOrIdBinder = createQueryStringBinder[FilterOrId]
  implicit val FilterOrIdSeqBinder = createQueryStringBinder[Seq[FilterOrId]]
  implicit val TablePageSeqBinder = createQueryStringBinder[Seq[PageOrder]]
}