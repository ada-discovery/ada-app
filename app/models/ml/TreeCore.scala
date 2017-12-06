package models.ml

import models.ml.classification.ValueOrSeq._

case class TreeCore(
  maxDepth: ValueOrSeq[Int] = Left(None),
  maxBins: ValueOrSeq[Int] = Left(None),
  minInstancesPerNode: ValueOrSeq[Int] = Left(None),
  minInfoGain: ValueOrSeq[Double] = Left(None),
  seed: Option[Long] = None
)