package services.stats.calc

import services.stats.{Calculator, CalculatorTypePack}

import scala.collection.mutable

trait SeqBinAggCalcTypePack[ACCUM] extends CalculatorTypePack {
  type IN = Seq[Option[Double]]
  type OUT = Traversable[(Seq[BigDecimal], Option[Double])]
  type INTER = mutable.ArraySeq[ACCUM]
  type OPT = Seq[NumericDistributionOptions]
  type FLOW_OPT = Seq[NumericDistributionFlowOptions]
  type SINK_OPT = FLOW_OPT
}

private[stats] class SeqBinAggCalc[ACCUM](calculator: Calculator[AllDefinedSeqBinAggCalcTypePack[ACCUM]]) extends SeqOptionInputCalc(calculator) with Calculator[SeqBinAggCalcTypePack[ACCUM]] {
  override type INN = Double

  override protected def toAllDefined = identity
}