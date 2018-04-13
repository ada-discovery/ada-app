package services.stats.calc

import akka.stream.scaladsl.Flow
import services.stats.Calculator

import util.GroupMapList
import services.stats.calc.GroupNumericDistributionCountsCalcIOTypes._
import util.AkkaStreamUtil._

object GroupNumericDistributionCountsCalcIOTypes {
  type IN[G] = (Option[G], Option[Double])
  type OUT[G] = Traversable[(Option[G], Traversable[(BigDecimal, Int)])]
  type INTER[G] = Traversable[((Option[G], Int), Int)]
  type OPTS = NumericDistributionOptions
  type FLOW_OPTS = NumericDistributionFlowOptions
}

private[stats] class GroupNumericDistributionCountsCalc[G] extends Calculator[IN[G], OUT[G], INTER[G], OPTS, FLOW_OPTS, FLOW_OPTS] with NumericDistributionCountsHelper {

  private val maxGroups = Int.MaxValue
  private val normalCalc = NumericDistributionCountsCalc

  override def fun(options: OPTS) =
    _.toGroupMap.map { case (group, values) =>
      (group, normalCalc.fun(options)(values))
    }

  override def flow(options: FLOW_OPTS) = {
    val stepSize = calcStepSize(
      options.columnCount,
      options.min,
      options.max,
      options.specialColumnForMax
    )

    val minBg = BigDecimal(options.min)
    val max = options.max

    val flatFlow = Flow[IN[G]].collect { case (g, Some(x)) => (g, x)}

    val groupBucketIndexFlow = Flow[(Option[G], Double)]
      .groupBy(maxGroups, _._1)
      .map { case (group, value) =>
        group -> calcBucketIndex(
          stepSize, options.columnCount, minBg, max)(value)
      }.mergeSubstreams

    flatFlow.via(groupBucketIndexFlow).via(groupCountFlow(maxGroups)).via(seqFlow)
  }

  override def postFlow(options: FLOW_OPTS) = { elements =>
    val stepSize = calcStepSize(
      options.columnCount,
      options.min,
      options.max,
      options.specialColumnForMax
    )

    val minBg = BigDecimal(options.min)

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
  def apply[G]: Calculator[IN[G], OUT[G], INTER[G], OPTS, FLOW_OPTS, FLOW_OPTS] = new GroupNumericDistributionCountsCalc[G]
}