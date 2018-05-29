package models.ml

import reactivemongo.bson.BSONObjectID

case class LearningSetting[T](
  featuresNormalizationType: Option[VectorTransformType.Value] = None,
  pcaDims: Option[Int] = None,
  trainingTestingSplit: Option[Double] = None,
  samplingRatios: Seq[(String, Double)] = Nil,
  repetitions: Option[Int] = None,
  crossValidationFolds: Option[Int] = None,
  crossValidationEvalMetric: Option[T] = None
)