package services.stats.calc

import services.stats.{Calculator, NoOptionsCalculatorTypePack}

object MultiCountDistinctCalc {

  type MultiCountDistinctCalcTypePack[T] =
    MultiCalcTypePack[CountDistinctCalcTypePack[T]] with NoOptionsCalculatorTypePack

  def apply[T]: Calculator[MultiCountDistinctCalcTypePack[T]] =
    MultiAdapterCalc.applyWithType(CountDistinctCalc[T])
}