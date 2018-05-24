package services.stats.calc

import services.stats.{Calculator, CalculatorTypePack}

import scala.collection.mutable

trait SeqBinCalcTypePack[ACCUM, AGG] extends CalculatorTypePack {
  type IN = Seq[Option[Double]]
  type OUT = Traversable[(Seq[BigDecimal], AGG)]
  type INTER = mutable.ArraySeq[ACCUM]
  type OPT = Seq[NumericDistributionOptions]
  type FLOW_OPT = Seq[NumericDistributionFlowOptions]
  type SINK_OPT = FLOW_OPT
}

private[stats] class SeqBinCalc[ACCUM, AGG](calculator: Calculator[AllDefinedSeqBinCalcTypePack[ACCUM, AGG]]) extends SeqOptionInputCalc(calculator) with Calculator[SeqBinCalcTypePack[ACCUM, AGG]] {
  override type INN = Double

  override protected def toAllDefined = identity
}