package services.stats.calc

import akka.stream.scaladsl.Sink
import services.stats.NoOptionsCalculator
import services.stats.calc.BasicStatsCalcIOTypes._
import play.api.Logger

object BasicStatsCalcIOTypes {
  type IN = Option[Double]
  type OUT = Option[BasicStatsResult]
  type INTER = BasicStatsAccum
}

object BasicStatsCalc extends NoOptionsCalculator[IN, OUT, INTER] {

  private val logger = Logger

  override def fun(o: Unit) = { values: Traversable[IN] =>
    val flattenedValues = values.flatten
    if (flattenedValues.nonEmpty) {
      val count = flattenedValues.size

      val min = flattenedValues.min
      val max = flattenedValues.max
      val mean = flattenedValues.sum / count

      // sum up the squares
      val meanSq = flattenedValues.foldLeft(0.0)((accum, value) => accum + value * value) / count

      val variance = meanSq - mean * mean

      Some(BasicStatsResult(min, max, mean, variance, Math.sqrt(variance), count, values.size - count))
    } else
      None
  }

  override def sink(o: Unit) =
    Sink.fold[BasicStatsAccum, Option[Double]](
      BasicStatsAccum(Double.MaxValue, Double.MinValue, 0, 0, 0, 0)
    ) {
      case (accum, value) =>
        value match {
          case Some(value) =>
            BasicStatsAccum(
              Math.min(accum.min, value),
              Math.max(accum.max, value),
              accum.sum + value,
              accum.sqSum + value * value,
              accum.count + 1,
              accum.undefinedCount
            )

          case None => accum.copy(undefinedCount = accum.undefinedCount + 1)
        }
    }

  override def postSink(o: Unit) = { accum: INTER =>
    if (accum.count > 0) {
      val mean = accum.sum / accum.count

      // sum up the squares
      val meanSq = accum.sqSum / accum.count
      val variance = meanSq - mean * mean

      Some(BasicStatsResult(accum.min, accum.max, mean, variance, Math.sqrt(variance), accum.count, accum.undefinedCount))
    } else
      None
  }
}

case class BasicStatsResult(
  min: Double,
  max: Double,
  mean: Double,
  variance: Double,
  standardDeviation: Double,
  definedCount: Int,
  undefinedCount: Int
)

case class BasicStatsAccum(
  min: Double,
  max: Double,
  sum: Double,
  sqSum: Double,
  count: Int,
  undefinedCount: Int
)