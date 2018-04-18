package services.stats.calc

import services.stats.{Calculator, CalculatorTypePack}

import scala.collection.mutable

trait NumericDistributionCountsCalcTypePack extends CalculatorTypePack {
  type IN = Option[Double]
  type OUT = Traversable[(BigDecimal, Int)]
  type INTER = mutable.ArraySeq[Int]
  type OPT = NumericDistributionOptions
  type FLOW_OPT = NumericDistributionFlowOptions
  type SINK_OPT = FLOW_OPT
}

private[stats] object NumericDistributionCountsCalcAux extends OptionInputCalc(AllDefinedNumericDistributionCountsCalc.apply) with Calculator[NumericDistributionCountsCalcTypePack]

object NumericDistributionCountsCalc {
  def apply: Calculator[NumericDistributionCountsCalcTypePack] = NumericDistributionCountsCalcAux
}