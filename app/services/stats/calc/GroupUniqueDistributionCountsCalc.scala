package services.stats.calc

import akka.stream.scaladsl.Sink
import services.stats.NoOptionsCalculator

import scala.collection.mutable.{Map => MMap}
import util.GroupMapList
import services.stats.calc.GroupUniqueDistributionCountsCalcIOType._

object GroupUniqueDistributionCountsCalcIOType {
  type IN[G, T] = (Option[G], Option[T])
  type OUT[G, T] = Traversable[(Option[G], Traversable[(Option[T], Int)])]
  type INTER[G,T] = MMap[(Option[G], Option[T]), Int]
}

private class GroupUniqueDistributionCountsCalc[G,T] extends NoOptionsCalculator[IN[G,T], OUT[G,T], INTER[G,T]] {

  private val normalCalc = new UniqueDistributionCountsCalc[T]

  override def fun(options: Unit) =
    _.toGroupMap.map { case (group, values) =>
      (group, normalCalc.fun()(values))
    }

  override def sink(options: Unit) =
    Sink.fold[INTER[G, T], (Option[G], Option[T])](
      MMap[(Option[G], Option[T]), Int]()
    ){ case (map, value) =>
      val count = map.getOrElse(value, 0)
      map.update(value, count)
      map
    }

  override def postSink(options: Unit) =
    _.map { case ((group, value), count) => (group, (value, count)) }.toGroupMap
}

object GroupUniqueDistributionCountsCalc {
  def apply[G, T]: NoOptionsCalculator[IN[G,T], OUT[G,T], INTER[G,T]] = new GroupUniqueDistributionCountsCalc[G,T]
}