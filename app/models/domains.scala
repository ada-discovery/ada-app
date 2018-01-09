package models

import com.fasterxml.jackson.core.JsonParseException
import controllers.{BSONObjectIDQueryStringBindable, EnumStringBindable}
import play.api.mvc.QueryStringBindable
import dataaccess.JsonUtil
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
import models.ml._
import models.ml.ClassificationResult.classificationSettingFormat
import models.ml.RegressionResult.regressionSettingFormat

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

  class JsonQueryStringBinder[E:Format](implicit stringBinder: QueryStringBindable[String]) extends QueryStringBindable[E] {

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

  implicit val FilterConditionQueryStringBinder = new JsonQueryStringBinder[Seq[FilterCondition]]
  implicit val FilterQueryStringBinder = new JsonQueryStringBinder[Filter]
  implicit val FieldTypeIdsQueryStringBinder = new JsonQueryStringBinder[Seq[FieldTypeId.Value]]
  implicit val BSONObjectIDQueryStringBinder = BSONObjectIDQueryStringBindable
  implicit val FilterOrIdBinder = new JsonQueryStringBinder[FilterOrId]
  implicit val FilterOrIdSeqBinder = new JsonQueryStringBinder[Seq[FilterOrId]]
  implicit val TablePageSeqBinder = new JsonQueryStringBinder[Seq[PageOrder]]

  implicit val ClassificationSettingBinder = new JsonQueryStringBinder[ClassificationSetting]
  implicit val RegressionSettingBinder = new JsonQueryStringBinder[RegressionSetting]

  implicit val vectorTransformTypeQueryStringBinder = new EnumStringBindable(VectorTransformType)
  implicit val classificationEvalMetricQueryStringBinder = new EnumStringBindable(ClassificationEvalMetric)
  implicit val regressionEvalMetricQueryStringBinder = new EnumStringBindable(RegressionEvalMetric)
}