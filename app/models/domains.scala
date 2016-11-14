package models

import controllers.BSONObjectIDQueryStringBindable
import play.api.mvc.QueryStringBindable
import util.JsonUtil
import dataaccess.{EnumFormat, BSONObjectIdentity}
import models.FilterCondition.{filterConditionFormat, filterFormat}
import models.DataSetFormattersAndIds.enumTypeFormat
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import java.util.{UUID, Date}
import reactivemongo.play.json.BSONFormats._
import play.api.libs.json._

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

  def createEitherBinder[L, R](
    implicit leftBinder: QueryStringBindable[L], rightBinder: QueryStringBindable[R]
  ) = new QueryStringBindable[Either[L, R]] {

    override def bind(
      key: String,
      params: Map[String, Seq[String]]
    ): Option[Either[String, Either[L, R]]] = {
      for {
        left <- leftBinder.bind(key, params)
        right <- rightBinder.bind(key, params)
      } yield {
        left match {
          case Right(value) => Right(Left(value))
          case Left(_) => right match {
            case Right(value) => Right(Right(value))
            case Left(_) => Left("Unable to bind from String to " + key)
          }
        }
      }
    }

    override def unbind(key: String, either: Either[L, R]): String =
      either match {
        case Left(value) => leftBinder.unbind(key, value)
        case Right(value) => rightBinder.unbind(key, value)
      }
  }

  implicit val FilterConditionQueryStringBinder = JsonUtil.createQueryStringBinder[Seq[FilterCondition]]
  implicit val FilterQueryStringBinder = JsonUtil.createQueryStringBinder[Filter]
  implicit val FieldTypeIdsQueryStringBinder = JsonUtil.createQueryStringBinder[Seq[FieldTypeId.Value]]

  implicit val BSONObjectIDQueryStringBinder = BSONObjectIDQueryStringBindable
  implicit val EitherBinder = createEitherBinder[Seq[FilterCondition], BSONObjectID]
}