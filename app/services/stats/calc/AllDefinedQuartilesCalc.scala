package services.stats.calc

import services.stats.CalculatorHelper.FullDataCalculator
import services.stats.FullDataCalculatorAdapter
import services.stats.calc.AllDefinedQuartilesCalcIOTypes._

object AllDefinedQuartilesCalcIOTypes {
  type IN[T] = T
  type OUT[T] = Option[Quartiles[T]]
  type OPT[T] = T => Double
}

private class AllDefinedQuartilesCalc[T: Ordering] extends FullDataCalculatorAdapter[IN[T], OUT[T], OPT[T]] {

  /**
    * Calculate quartiles for boxplots.
    * Generation is meant for Tukey boxplots.
    *
    * @param elements sequence of elements.
    * @return 5-value tuple with (lower 1.5 IQR whisker, lower quantile, median, upper quantile, upper 1.5 IQR whisker)
    */
  override def fun(toDouble: T => Double)  = { elements =>
    elements.headOption.map { _ =>
      val sorted = elements.toSeq.sorted
      val length = sorted.size

      // median
      val median = sorted(length / 2)

      // upper quartile
      val upperQuantile = sorted(3 * length / 4)

      // lower quartile
      val lowerQuantile = sorted(length / 4)

      val upperQuantileDouble = toDouble(upperQuantile)
      val lowerQuantileDouble = toDouble(lowerQuantile)
      val iqr = upperQuantileDouble - lowerQuantileDouble

      val upperWhiskerValue = upperQuantileDouble + 1.5 * iqr
      val lowerWhiskerValue = lowerQuantileDouble - 1.5 * iqr

      val lowerWhisker = sorted.find(value => toDouble(value) >= lowerWhiskerValue).getOrElse(sorted.last)
      val upperWhisker = sorted.reverse.find(value => toDouble(value) <= upperWhiskerValue).getOrElse(sorted.head)

      Quartiles(lowerWhisker, lowerQuantile, median, upperQuantile, upperWhisker)
    }
  }
}

case class Quartiles[T <% Ordered[T]](
    lowerWhisker: T,
    lowerQuantile: T,
    median: T,
    upperQuantile: T,
    upperWhisker: T
  ) {
    def ordering = implicitly[Ordering[T]]
    def toSeq: Seq[T] = Seq(lowerWhisker, lowerQuantile, median, upperQuantile, upperWhisker)
}

object AllDefinedQuartilesCalc {
  def apply[T: Ordering]: FullDataCalculator[T, Option[Quartiles[T]], T => Double] = new AllDefinedQuartilesCalc[T]
}