package models.ml

import java.{util => ju}

import dataaccess.BSONObjectIdentity
import models.FilterCondition.FilterOrId
import models.json.{EitherFormat, EnumFormat, FlattenFormat, TupleFormat, OptionFormat}
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import play.api.libs.json._

case class ClassificationResult(
  _id: Option[BSONObjectID],
  setting: ClassificationSetting,
  trainingStats: ClassificationMetricStats,
  testStats: ClassificationMetricStats,
  replicationStats: Option[ClassificationMetricStats] = None,
  trainingBinCurves: Seq[BinaryClassificationCurves] = Nil,
  testBinCurves: Seq[BinaryClassificationCurves] = Nil,
  replicationBinCurves: Seq[BinaryClassificationCurves] = Nil,
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

case class BinaryClassificationCurves(
  // ROC - FPR vs TPR (false positive rate vs true positive rate)
  roc: Seq[(Double, Double)],
  // PR - recall vs precision
  precisionRecall: Seq[(Double, Double)],
  // threshold vs F-Measure: curve with beta = 1.0.
  fMeasureThreshold: Seq[(Double, Double)],
  // threshold vs precision
  precisionThreshold: Seq[(Double, Double)],
  // threshold vs recall
  recallThreshold: Seq[(Double, Double)]
)

case class ClassificationSetting(
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
  binCurvesNumBins: Option[Int]
) {
  def fieldNamesToLoads =
    if (inputFieldNames.nonEmpty) (inputFieldNames ++ Seq(outputFieldName)).toSet.toSeq else Nil

  def learningSetting =
    LearningSetting(featuresNormalizationType, pcaDims, trainingTestingSplit, replicationFilterId, samplingRatios, repetitions, crossValidationFolds)
}

object ClassificationResult {
  implicit val filterOrIdFormat = new EitherFormat[Seq[models.FilterCondition], BSONObjectID]

  implicit val tuppleFormat = TupleFormat[String, Double]
  implicit val vectorTransformTypeFormat = EnumFormat.enumFormat(VectorTransformType)
  implicit val classificationSettingFormat = Json.format[ClassificationSetting]
  implicit val classificationMetricStatsValuesFormat = Json.format[MetricStatsValues]
  implicit val classificationMetricStatsFormat = Json.format[ClassificationMetricStats]
  implicit val doubleTupleFormat = TupleFormat[Double, Double]
  implicit val binaryClassificationCurvesFormat = Json.format[BinaryClassificationCurves]
//  implicit val binaryClassifcationCurvesOptionalFormat = new OptionFormat[BinaryClassificationCurves]
  implicit val classificationResultFormat = new FlattenFormat(Json.format[ClassificationResult], "-", Set("_id", "filterId", "replicationFilterId", "mlModelId"))

  implicit object ClassificationResultIdentity extends BSONObjectIdentity[ClassificationResult] {
    def of(entity: ClassificationResult): Option[BSONObjectID] = entity._id
    protected def set(entity: ClassificationResult, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}