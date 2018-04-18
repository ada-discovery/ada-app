package services.stats.calc

import util.AkkaStreamUtil._
import services.stats.{Calculator, NoOptionsCalculatorTypePack}

trait UniqueDistributionCountsCalcTypePack[T] extends NoOptionsCalculatorTypePack {
  type IN = Option[T]
  type OUT = Traversable[(Option[T], Int)]
  type INTER = OUT
}

private[stats] class UniqueDistributionCountsCalc[T] extends Calculator[UniqueDistributionCountsCalcTypePack[T]] {

  private val maxGroups = Int.MaxValue

  override def fun(options: Unit) =
    _.groupBy(identity).map { case (value, seq) => (value, seq.size) }

  override def flow(options: Unit) =
    groupCountFlow[Option[T]](maxGroups).via(seqFlow)

  override def postFlow(options: Unit) = identity
}

object UniqueDistributionCountsCalc {
  def apply[T]: Calculator[UniqueDistributionCountsCalcTypePack[T]] = new UniqueDistributionCountsCalc[T]
}