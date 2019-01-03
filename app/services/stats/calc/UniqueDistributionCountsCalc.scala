package services.stats.calc

import services.stats.Calculator

object UniqueDistributionCountsCalc {
  type UniqueDistributionCountsCalcTypePack[T] = CountDistinctCalcTypePack[Option[T]]

  def apply[T]: Calculator[UniqueDistributionCountsCalcTypePack[T]] = CountDistinctCalc[Option[T]]
}