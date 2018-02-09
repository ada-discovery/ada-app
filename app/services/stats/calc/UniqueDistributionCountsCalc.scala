package services.stats.calc

import akka.stream.scaladsl.Sink
import services.stats.NoOptionsCalculator
import scala.collection.mutable.{Map => MMap}
import services.stats.calc.UniqueDistributionCountsCalcIOType._

object UniqueDistributionCountsCalcIOType {
  type IN[T] = Option[T]
  type OUT[T] = Traversable[(Option[T], Int)]
  type INTER[T] = MMap[Option[T], Int]
}

private class UniqueDistributionCountsCalc[T] extends NoOptionsCalculator[IN[T], OUT[T], INTER[T]] {

  override def fun(options: Unit) =
    _.groupBy(identity).map { case (value, seq) => (value, seq.size) }

  override def sink(options: Unit) =
    Sink.fold[INTER[T], Option[T]](
      MMap[Option[T],Int]()
    ){ case (map, value) =>
      val count = map.getOrElse(value, 0)
      map.update(value, count)
      map
    }

  override def postSink(options: Unit) = identity
}

object UniqueDistributionCountsCalc {
  def apply[T]: NoOptionsCalculator[IN[T], OUT[T], INTER[T]] = new UniqueDistributionCountsCalc[T]
}