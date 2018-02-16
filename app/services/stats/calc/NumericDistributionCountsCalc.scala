package services.stats.calc

import akka.stream.scaladsl.{Flow, Keep}
import services.stats.Calculator
import services.stats.calc.NumericDistributionCountsCalcIOType._

import scala.collection.mutable

object NumericDistributionCountsCalcIOType {
  type IN[T] = Option[T]
  type OUT[T] = Traversable[(BigDecimal, Int)]
  type INTER[T] = mutable.ArraySeq[Int]
  type OPTIONS[T] = NumericDistributionOptions[T]
  type SINK_OPTIONS[T] = NumericDistributionSinkOptions[T]
}

private class NumericDistributionCountsCalc[T: Numeric] extends Calculator[IN[T], OUT[T], INTER[T], OPTIONS[T], SINK_OPTIONS[T], SINK_OPTIONS[T]] {

  private val allDefinedCalc = AllDefinedNumericDistributionCountsCalc[T]

  override def fun(options: NumericDistributionOptions[T]) = { values =>
    allDefinedCalc.fun(options)(values.flatten)
  }

  override def sink(options: SINK_OPTIONS[T]) = {
    val allDefinedSink = allDefinedCalc.sink(options)
    val flatFlow = Flow[Option[T]].collect { case Some(x) => x}
    flatFlow.toMat(allDefinedSink)(Keep.right)
  }

  override def postSink(options: SINK_OPTIONS[T]) =
    allDefinedCalc.postSink(options)
}

object NumericDistributionCountsCalc {
  def apply[T: Numeric]: Calculator[IN[T], OUT[T], INTER[T], OPTIONS[T], SINK_OPTIONS[T], SINK_OPTIONS[T]] = new NumericDistributionCountsCalc[T]
}