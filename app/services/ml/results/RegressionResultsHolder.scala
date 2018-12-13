package services.ml.results

import models.ml.RegressionEvalMetric

case class RegressionResultsHolder(
  performanceResults: Traversable[RegressionPerformance],
  counts: Traversable[Long],
  expectedAndActualOutputs: Traversable[Traversable[Seq[(Double, Double)]]]
)

case class RegressionResultsAuxHolder(
  evalResults: Traversable[(RegressionEvalMetric.Value, Double, Seq[Double])],
  count: Long,
  expectedAndActualOutputs: Traversable[Seq[(Double, Double)]]
)
