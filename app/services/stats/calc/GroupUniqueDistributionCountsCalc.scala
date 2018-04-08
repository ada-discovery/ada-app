package services.stats.calc

import akka.stream.scaladsl.{Keep, Sink}
import services.stats.NoOptionsCalculator

import util.GroupMapList
import util.AkkaStreamUtil._
import services.stats.calc.GroupUniqueDistributionCountsCalcIOType._

object GroupUniqueDistributionCountsCalcIOType {
  type IN[G, T] = (Option[G], Option[T])
  type OUT[G, T] = Traversable[(Option[G], Traversable[(Option[T], Int)])]
  type INTER[G,T] = Traversable[((Option[G], Option[T]), Int)]
}

private class GroupUniqueDistributionCountsCalc[G,T] extends NoOptionsCalculator[IN[G,T], OUT[G,T], INTER[G,T]] {

  private val maxGroups = Int.MaxValue

  private val normalCalc = new UniqueDistributionCountsCalc[T]

  override def fun(options: Unit) =
    _.toGroupMap.map { case (group, values) =>
      (group, normalCalc.fun()(values))
    }

  override def flow(options: Unit) =
    groupCountFlow[(Option[G], Option[T])](maxGroups).via(seqFlow)

  override def postFlow(options: Unit) =
    _.map { case ((group, value), count) => (group, (value, count)) }.toGroupMap
}

object GroupUniqueDistributionCountsCalc {
  def apply[G, T]: NoOptionsCalculator[IN[G,T], OUT[G,T], INTER[G,T]] = new GroupUniqueDistributionCountsCalc[G,T]
}