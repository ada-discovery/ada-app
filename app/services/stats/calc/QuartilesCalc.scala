package services.stats.calc

import services.stats.Calculator
import services.stats.calc.QuartilesCalcIOTypes._

object QuartilesCalcIOTypes {
  type IN[T] = Option[T]
  type OUT[T] = Option[Quartiles[T]]
  type INTER[T] = Traversable[T]
  type OPT[T] = T => Double
}

private[stats] class QuartilesCalc[T: Ordering] extends OptionInputCalc[T, OUT[T], Traversable[T], OPT[T], Unit, OPT[T]] {
  override protected val allDefinedCalc = AllDefinedQuartilesCalc[T]
}

object QuartilesCalc {
  def apply[T: Ordering]: Calculator[IN[T], OUT[T], INTER[T], OPT[T], Unit, OPT[T]] = new QuartilesCalc[T]
}