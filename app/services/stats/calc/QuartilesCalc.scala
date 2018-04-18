package services.stats.calc

import services.stats.{Calculator, CalculatorTypePack}

trait QuartilesCalcTypePack[T] extends CalculatorTypePack {
  type IN = Option[T]
  type OUT = Option[Quartiles[T]]
  type INTER = Traversable[T]
  type OPT = T => Double
  type FLOW_OPT = Unit
  type SINK_OPT = OPT
}

private[stats] class QuartilesCalc[T: Ordering] extends OptionInputCalc(AllDefinedQuartilesCalc[T]) with Calculator[QuartilesCalcTypePack[T]]

object QuartilesCalc {
  def apply[T: Ordering]: Calculator[QuartilesCalcTypePack[T]] = new QuartilesCalc[T]
}