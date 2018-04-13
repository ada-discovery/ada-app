package services.stats.calc

import services.stats.CalculatorHelper.NoOptionsCalculator
import services.stats.calc.CumulativeOrderedCountsCalcIOTypes._

object CumulativeOrderedCountsCalcIOTypes {
  type IN[T] = Option[T]
  type OUT[T] = Traversable[(T, Int)]
}

private class CumulativeOrderedCountsCalc[T: Ordering] extends NoOptionsCalculator[IN[T], OUT[T], OUT[T]] with CumulativeOrderedCountsCalcFun {

  private val basicCalc = UniqueDistributionCountsCalc[T]

  override def fun(options: Unit) =
    (basicCalc.fun()(_)) andThen sortAndCount[T]

  override def flow(options: Unit) =
    basicCalc.flow() map sortAndCount[T]

  override def postFlow(options: Unit) = identity
}

trait CumulativeOrderedCountsCalcFun {

  protected def sortAndCount[T: Ordering](values: UniqueDistributionCountsCalcIOTypes.OUT[T]) = {
    val ordered = values.toSeq.collect { case (Some(value), count) => (value, count) }.sortBy(_._1)
    val sums = ordered.scanLeft(0: Int) { case (sum, (_, count)) => count + sum }
    sums.tail.zip(ordered).map { case (sum, (value, _)) => (value, sum) }
  }
}

object CumulativeOrderedCountsCalc {
  def apply[T: Ordering]: NoOptionsCalculator[IN[T], OUT[T], OUT[T]] = new CumulativeOrderedCountsCalc[T]
}