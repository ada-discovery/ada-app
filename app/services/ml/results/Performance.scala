package services.ml.results

import models.ml.{ClassificationEvalMetric, RegressionEvalMetric}

abstract class Performance[T <: Enumeration#Value] {
  def evalMetric: T
  def trainingTestReplicationResults: Traversable[(Double, Double, Option[Double])]
}

case class ClassificationPerformance (
  val evalMetric: ClassificationEvalMetric.Value,
  val trainingTestReplicationResults: Traversable[(Double, Double, Option[Double])]
) extends Performance[ClassificationEvalMetric.Value]

case class RegressionPerformance(
  val evalMetric: RegressionEvalMetric.Value,
  val trainingTestReplicationResults: Traversable[(Double, Double, Option[Double])]
) extends Performance[RegressionEvalMetric.Value]