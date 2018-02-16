package services.stats.calc

import akka.stream.scaladsl.{Keep, Sink}
import services.stats.NoOptionsCalculator

import scala.collection.mutable.{Map => MMap}
import util.AkkaStreamUtil._
import services.stats.calc.UniqueDistributionCountsCalcIOType._

object UniqueDistributionCountsCalcIOType {
  type IN[T] = Option[T]
  type OUT[T] = Traversable[(Option[T], Int)]
}

private class UniqueDistributionCountsCalc[T] extends NoOptionsCalculator[IN[T], OUT[T], OUT[T]] {

  private val maxGroups = Int.MaxValue

  override def fun(options: Unit) =
    _.groupBy(identity).map { case (value, seq) => (value, seq.size) }

  override def sink(options: Unit) =
    groupCountFlow[Option[T]](maxGroups).toMat(Sink.seq)(Keep.right)

  override def postSink(options: Unit) = identity
}

object UniqueDistributionCountsCalc {
  def apply[T]: NoOptionsCalculator[IN[T], OUT[T], OUT[T]] = new UniqueDistributionCountsCalc[T]
}