package controllers

import com.fasterxml.jackson.core.JsonParseException
import controllers.mpower.AggFunction
import models.Filter._
import controllers.FilterConditionExtraFormats.eitherFilterOrIdFormat
import models.{AggType, CorrelationType, FieldTypeId, Filter}
import models.ml._
import models.DataSetFormattersAndIds.enumTypeFormat
import models.ml.classification.ClassificationResult._
import models.ml.regression.RegressionResult._
import org.incal.core.FilterCondition
import org.incal.play.PageOrder
import org.incal.spark_ml.models.classification.ClassificationEvalMetric
import org.incal.spark_ml.models.regression.RegressionEvalMetric
import org.incal.spark_ml.models.setting.{ClassificationRunSpec, RegressionRunSpec}
import org.incal.spark_ml.models.VectorScalerType
import play.api.libs.json.{Format, Json}
import play.api.mvc.QueryStringBindable

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

  implicit val filterConditionQueryStringBinder = new JsonQueryStringBinder[Seq[FilterCondition]]
  implicit val filterQueryStringBinder = new JsonQueryStringBinder[Filter]
  implicit val fieldTypeIdsQueryStringBinder = new JsonQueryStringBinder[Seq[FieldTypeId.Value]]
  implicit val BSONObjectIDQueryStringBinder = BSONObjectIDQueryStringBindable
  implicit val filterOrIdBinder = new JsonQueryStringBinder[FilterOrId]
  implicit val filterOrIdSeqBinder = new JsonQueryStringBinder[Seq[FilterOrId]]
  implicit val tablePageSeqBinder = new JsonQueryStringBinder[Seq[PageOrder]]

  implicit val classificationRunSpecBinder = new JsonQueryStringBinder[ClassificationRunSpec]
  implicit val regressionRunSpecBinder = new JsonQueryStringBinder[RegressionRunSpec]

  implicit val vectorScalerTypeQueryStringBinder = new EnumStringBindable(VectorScalerType)
  implicit val classificationEvalMetricQueryStringBinder = new EnumStringBindable(ClassificationEvalMetric)
  implicit val regressionEvalMetricQueryStringBinder = new EnumStringBindable(RegressionEvalMetric)
  implicit val aggFunctionQueryStringBinder = new EnumStringBindable(AggFunction)
  implicit val aggTypeQueryStringBinder = new EnumStringBindable(AggType)
  implicit val correlationTypeStringBinder = new EnumStringBindable(CorrelationType)
}