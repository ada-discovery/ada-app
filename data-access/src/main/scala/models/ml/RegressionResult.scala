package models.ml

import java.{util => ju}

import dataaccess.BSONObjectIdentity
import models.FilterCondition.FilterOrId
import models.json.{EitherFormat, EnumFormat, FlattenFormat, TupleFormat, OptionFormat}
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import play.api.libs.json._

case class RegressionResult(
  _id: Option[BSONObjectID],
  setting: RegressionSetting,
  trainingStats: RegressionMetricStats,
  testStats: RegressionMetricStats,
  replicationStats: Option[RegressionMetricStats] = None,
  timeCreated: ju.Date = new ju.Date()
)

case class RegressionMetricStats(
  mse: MetricStatsValues,
  rmse: MetricStatsValues,
  r2: MetricStatsValues,
  mae: MetricStatsValues
)

case class RegressionSetting(
  mlModelId: BSONObjectID,
  outputFieldName: String,
  inputFieldNames: Seq[String],
  filterId: Option[BSONObjectID],
  featuresNormalizationType: Option[VectorTransformType.Value],
  featuresSelectionNum: Option[Int],
  pcaDims: Option[Int],
  trainingTestingSplit: Option[Double],
  replicationFilterId: Option[BSONObjectID],
  samplingRatios: Seq[(String, Double)],
  repetitions: Option[Int],
  crossValidationFolds: Option[Int],
  crossValidationEvalMetric: Option[RegressionEvalMetric.Value]
) {
  def fieldNamesToLoads =
    if (inputFieldNames.nonEmpty) (inputFieldNames ++ Seq(outputFieldName)).toSet.toSeq else Nil

  def learningSetting =
    LearningSetting[RegressionEvalMetric.Value](featuresNormalizationType, pcaDims, trainingTestingSplit, replicationFilterId, samplingRatios, repetitions, crossValidationFolds, crossValidationEvalMetric)
}

object RegressionResult {
  implicit val filterOrIdFormat = EitherFormat[Seq[models.FilterCondition], BSONObjectID]

  implicit val tupleFormat = TupleFormat[String, Double]
  implicit val vectorTransformTypeFormat = EnumFormat.enumFormat(VectorTransformType)
  implicit val regressionEvalMetricFormat = EnumFormat.enumFormat(RegressionEvalMetric)
  implicit val regressionSettingFormat = Json.format[RegressionSetting]
  implicit val regressionMetricStatsValuesFormat = Json.format[MetricStatsValues]
  implicit val regressionMetricStatsFormat = Json.format[RegressionMetricStats]

  implicit val regressionResultFormat = new FlattenFormat(Json.format[RegressionResult], "-", Set("_id", "filterId", "replicationFilterId", "mlModelId"))

  implicit object RegressionResultIdentity extends BSONObjectIdentity[RegressionResult] {
    def of(entity: RegressionResult): Option[BSONObjectID] = entity._id
    protected def set(entity: RegressionResult, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}