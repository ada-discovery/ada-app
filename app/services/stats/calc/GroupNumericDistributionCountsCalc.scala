package services.stats.calc


import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import services.stats.Calculator

import util.GroupMapList
import services.stats.calc.GroupNumericDistributionCountsCalcIOType._
import util.AkkaStreamUtil._

object GroupNumericDistributionCountsCalcIOType {
  type IN[G, T] = (Option[G], Option[T])
  type OUT[G] = Traversable[(Option[G], Traversable[(BigDecimal, Int)])]
  type INTER[G] = Traversable[((Option[G], Int), Int)]
  type OPTIONS[T] = NumericDistributionOptions[T]
  type SINK_OPTIONS[T] = NumericDistributionSinkOptions[T]
}

private class GroupNumericDistributionCountsCalc[G, T: Numeric] extends Calculator[IN[G,T], OUT[G], INTER[G], OPTIONS[T], SINK_OPTIONS[T], SINK_OPTIONS[T]]
  with NumericDistributionCountsHelper[T] {

  override val numeric = implicitly[Numeric[T]]
  private val maxGroups = Int.MaxValue

  private val normalCalc = NumericDistributionCountsCalc[T]

  override def fun(options: OPTIONS[T]) =
    _.toGroupMap.map { case (group, values) =>
      (group, normalCalc.fun(options)(values))
    }

  override def flow(options: SINK_OPTIONS[T]) = {
    val stepSize = calcStepSize(
      options.columnCount,
      options.min,
      options.max,
      options.specialColumnForMax
    )

    val minBg = BigDecimal(numeric.toDouble(options.min))
    val max = numeric.toDouble(options.max)

    val flatFlow = Flow[IN[G, T]].collect { case (g, Some(x)) => (g, x)}

    val groupBucketIndexFlow = Flow[(Option[G], T)]
      .groupBy(maxGroups, _._1)
      .map { case (group, value) =>
        group -> calcBucketIndex(
          stepSize, options.columnCount, minBg, max)(
          numeric.toDouble(value)
        )
      }.mergeSubstreams

    flatFlow.via(groupBucketIndexFlow).via(groupCountFlow(maxGroups)).via(seqFlow)
  }

  override def postFlow(options: SINK_OPTIONS[T]) = { elements =>
    val stepSize = calcStepSize(
      options.columnCount,
      options.min,
      options.max,
      options.specialColumnForMax
    )

    val minBg = BigDecimal(numeric.toDouble(options.min))

    val groupIndexCounts = elements.map { case ((group, index), count) => (group, (index, count))}.toGroupMap

    groupIndexCounts.map { case (group, counts) =>
      val indexCountMap = counts.toMap

      val xValueCounts =
        for (index <- 0 to options.columnCount - 1) yield {
          val count = indexCountMap.get(index).getOrElse(0)
          val xValue = minBg + (index * stepSize)
          (xValue, count)
        }
      (group, xValueCounts)
    }
  }
}

object GroupNumericDistributionCountsCalc {
  def apply[G, T: Numeric]: Calculator[IN[G,T], OUT[G], INTER[G], OPTIONS[T], SINK_OPTIONS[T], SINK_OPTIONS[T]] = new GroupNumericDistributionCountsCalc[G,T]
}