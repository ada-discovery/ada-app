package models.ml.classification

import dataaccess.BSONObjectIdentity
import models.json.{EnumFormat, FlattenFormat, TupleFormat}
import org.incal.spark_ml.models.VectorScalerType
import org.incal.spark_ml.models.classification.ClassificationEvalMetric
import org.incal.spark_ml.models.result.{BinaryClassificationCurves, ClassificationMetricStats, ClassificationResult, MetricStatsValues}
import org.incal.spark_ml.models.setting.{ClassificationLearningSetting, ClassificationRunSpec, IOSpec}
import play.api.libs.json.{Json, _}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

object ClassificationResult {

  implicit val classificationResultFormat: Format[ClassificationResult] = {
    implicit val VectorScalerTypeFormat = EnumFormat.enumFormat(VectorScalerType)
    implicit val classificationEvalMetricFormat = EnumFormat.enumFormat(ClassificationEvalMetric)
    createClassificationResultFormat(VectorScalerTypeFormat, classificationEvalMetricFormat)
  }

  implicit val classificationRunSpecFormat: Format[ClassificationRunSpec] = {
    implicit val VectorScalerTypeFormat = EnumFormat.enumFormat(VectorScalerType)
    implicit val classificationEvalMetricFormat = EnumFormat.enumFormat(ClassificationEvalMetric)
    createClassificationRunSpecFormat(VectorScalerTypeFormat, classificationEvalMetricFormat)
  }

  def createClassificationRunSpecFormat(
    implicit VectorScalerTypeFormat: Format[VectorScalerType.Value],
    classificationEvalMetricFormat: Format[ClassificationEvalMetric.Value]
  ) = {
    implicit val tupleFormat = TupleFormat[String, Double]
    implicit val ioSpecFormat = Json.format[IOSpec]
    implicit val learningSettingFormat = Json.format[ClassificationLearningSetting]

    Json.format[ClassificationRunSpec]
  }

  def createClassificationResultFormat(
    implicit VectorScalerTypeFormat: Format[VectorScalerType.Value],
    classificationEvalMetricFormat: Format[ClassificationEvalMetric.Value]
  ) = {
    implicit val classificationSettingFormat = createClassificationRunSpecFormat(VectorScalerTypeFormat, classificationEvalMetricFormat)
    implicit val classificationMetricStatsValuesFormat = Json.format[MetricStatsValues]
    implicit val classificationMetricStatsFormat = Json.format[ClassificationMetricStats]
    implicit val doubleTupleFormat = TupleFormat[Double, Double]
    implicit val binaryClassificationCurvesFormat = Json.format[BinaryClassificationCurves]
    //  implicit val binaryClassifcationCurvesOptionalFormat = new OptionFormat[BinaryClassificationCurves]
    new FlattenFormat(Json.format[ClassificationResult], "-", Set("_id", "filterId", "replicationFilterId", "mlModelId"))
  }

  implicit object ClassificationResultIdentity extends BSONObjectIdentity[ClassificationResult] {
    def of(entity: ClassificationResult): Option[BSONObjectID] = entity._id
    protected def set(entity: ClassificationResult, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}