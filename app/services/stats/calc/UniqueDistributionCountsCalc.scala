package services.stats.calc

import util.AkkaStreamUtil._
import services.stats.CalculatorHelper.NoOptionsCalculator
import services.stats.calc.UniqueDistributionCountsCalcIOTypes._

object UniqueDistributionCountsCalcIOTypes {
  type IN[T] = Option[T]
  type OUT[T] = Traversable[(Option[T], Int)]
}

private[stats] class UniqueDistributionCountsCalc[T] extends NoOptionsCalculator[IN[T], OUT[T], OUT[T]] {

  private val maxGroups = Int.MaxValue

  override def fun(options: Unit) =
    _.groupBy(identity).map { case (value, seq) => (value, seq.size) }

  override def flow(options: Unit) =
    groupCountFlow[Option[T]](maxGroups).via(seqFlow)

  override def postFlow(options: Unit) = identity
}

object UniqueDistributionCountsCalc {
  def apply[T]: NoOptionsCalculator[IN[T], OUT[T], OUT[T]] = new UniqueDistributionCountsCalc[T]
}