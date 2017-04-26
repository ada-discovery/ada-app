package models.ml

case class TreeCore(
  maxDepth: Option[Int] = None,
  maxBins: Option[Int] = None,
  minInstancesPerNode: Option[Int] = None,
  minInfoGain: Option[Double] = None,
  seed: Option[Long] = None
)