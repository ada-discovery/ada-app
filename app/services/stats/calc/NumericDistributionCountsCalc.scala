package services.stats.calc

import services.stats.calc.NumericDistributionCountsCalcIOTypes._

import scala.collection.mutable

object NumericDistributionCountsCalcIOTypes {
  type IN = Option[Double]
  type OUT = Traversable[(BigDecimal, Int)]
  type INTER = mutable.ArraySeq[Int]
  type OPTS = NumericDistributionOptions
  type FLOW_OPTS = NumericDistributionFlowOptions
}

object NumericDistributionCountsCalc extends OptionInputCalc[Double, OUT, INTER, OPTS, FLOW_OPTS, FLOW_OPTS] {
  override protected val allDefinedCalc = AllDefinedNumericDistributionCountsCalc.apply
}