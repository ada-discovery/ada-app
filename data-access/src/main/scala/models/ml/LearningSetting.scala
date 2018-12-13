package models.ml

import org.incal.core.VectorScalerType

case class LearningSetting[T](
  featuresNormalizationType: Option[VectorScalerType.Value] = None,
  pcaDims: Option[Int] = None,
  trainingTestingSplit: Option[Double] = None,
  samplingRatios: Seq[(String, Double)] = Nil,
  repetitions: Option[Int] = None,
  crossValidationFolds: Option[Int] = None,
  crossValidationEvalMetric: Option[T] = None
)