package models.ml

case class LearningSetting(
  featuresNormalizationType: Option[VectorTransformType.Value] = None,
  pcaDims: Option[Int] = None,
  trainingTestingSplit: Option[Double] = None,
  samplingRatios: Seq[(String, Double)] = Nil,
  repetitions: Option[Int] = None,
  crossValidationFolds: Option[Int] = None
)