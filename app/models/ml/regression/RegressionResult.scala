package models.ml.regression

import dataaccess.BSONObjectIdentity
import models.json.{EnumFormat, FlattenFormat, TupleFormat}
import org.incal.spark_ml.models.VectorScalerType
import org.incal.spark_ml.models.regression.RegressionEvalMetric
import org.incal.spark_ml.models.result.{MetricStatsValues, RegressionMetricStats, RegressionResult}
import org.incal.spark_ml.models.setting.{ClassificationLearningSetting, IOSpec, RegressionLearningSetting, RegressionRunSpec}
import play.api.libs.json.{Json, _}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

object RegressionResult {

  implicit val regressionResultFormat: Format[RegressionResult] = {
    implicit val VectorScalerTypeFormat = EnumFormat.enumFormat(VectorScalerType)
    implicit val regressionEvalMetricFormat = EnumFormat.enumFormat(RegressionEvalMetric)
    createRegressionResultFormat(VectorScalerTypeFormat, regressionEvalMetricFormat)
  }

  implicit val regressionSettingFormat: Format[RegressionRunSpec] = {
    implicit val VectorScalerTypeFormat = EnumFormat.enumFormat(VectorScalerType)
    implicit val regressionEvalMetricFormat = EnumFormat.enumFormat(RegressionEvalMetric)
    createRegressionSettingFormat(VectorScalerTypeFormat, regressionEvalMetricFormat)
  }

  def createRegressionSettingFormat(
    implicit VectorScalerTypeFormat: Format[VectorScalerType.Value],
    regressionEvalMetricFormat: Format[RegressionEvalMetric.Value]
  ) = {
    implicit val tupleFormat = TupleFormat[String, Double]
    implicit val ioSpecFormat = Json.format[IOSpec]
    implicit val learningSettingFormat = Json.format[RegressionLearningSetting]

    Json.format[RegressionRunSpec]
  }

  def createRegressionResultFormat(
    implicit VectorScalerTypeFormat: Format[VectorScalerType.Value],
    regressionEvalMetricFormat: Format[RegressionEvalMetric.Value]
  ) = {
    implicit val regressionSettingFormat = createRegressionSettingFormat(VectorScalerTypeFormat, regressionEvalMetricFormat)
    implicit val regressionMetricStatsValuesFormat = Json.format[MetricStatsValues]
    implicit val regressionMetricStatsFormat = Json.format[RegressionMetricStats]
    new FlattenFormat(Json.format[RegressionResult], "-", Set("_id", "filterId", "replicationFilterId", "mlModelId"))
  }

  implicit object RegressionResultIdentity extends BSONObjectIdentity[RegressionResult] {
    def of(entity: RegressionResult): Option[BSONObjectID] = entity._id
    protected def set(entity: RegressionResult, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}
