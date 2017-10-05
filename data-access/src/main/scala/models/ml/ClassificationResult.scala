package models.ml

import java.{util => ju}

import dataaccess.BSONObjectIdentity
import models.FilterCondition.FilterOrId
import models.json.{EitherFormat, FlattenFormat}
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import play.api.libs.json._

case class ClassificationResult(
  _id: Option[BSONObjectID],
  setting: ClassificationSetting,
  trainingStats: ClassificationMetricStats,
  testStats: ClassificationMetricStats,
  timeCreated: ju.Date = new ju.Date()
)

case class ClassificationMetricStats(
  f1: MetricStatsValues,
  weightedPrecision: MetricStatsValues,
  weightedRecall: MetricStatsValues,
  accuracy: MetricStatsValues,
  areaUnderROC: Option[MetricStatsValues],
  areaUnderPR: Option[MetricStatsValues]
)

case class MetricStatsValues(
  mean: Double,
  min: Double,
  max: Double,
  variance: Double
)

case class ClassificationSetting(
  mlModelId: BSONObjectID,
  outputFieldName: String,
  inputFieldNames: Seq[String],
  filterId: Option[BSONObjectID],
  pcaDims: Option[Int],
  trainingTestingSplit: Option[Double],
  repetitions: Option[Int],
  crossValidationFolds: Option[Int]
) {
  def fieldNamesToLoads =
    if (inputFieldNames.nonEmpty) (inputFieldNames ++ Seq(outputFieldName)).toSet.toSeq else Nil

  def learningSetting =
    LearningSetting(pcaDims, trainingTestingSplit, repetitions, crossValidationFolds)
}

object ClassificationResult {
  implicit val filterOrIdFormat = new EitherFormat[Seq[models.FilterCondition], BSONObjectID]

  implicit val classificationSettingFormat = Json.format[ClassificationSetting]
  implicit val classificationMetricStatsValuesFormat = Json.format[MetricStatsValues]
  implicit val classificationMetricStatsFormat = Json.format[ClassificationMetricStats]
  implicit val classificationResultFormat = new FlattenFormat(Json.format[ClassificationResult], "-", Set("_id", "filterId", "mlModelId"))

  implicit object ClassificationResultIdentity extends BSONObjectIdentity[ClassificationResult] {
    def of(entity: ClassificationResult): Option[BSONObjectID] = entity._id
    protected def set(entity: ClassificationResult, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}