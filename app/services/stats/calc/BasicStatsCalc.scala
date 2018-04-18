package services.stats.calc

import akka.stream.scaladsl.Flow
import services.stats.{Calculator, NoOptionsCalculatorTypePack}

trait BasicStatsCalcTypePack extends NoOptionsCalculatorTypePack{
  type IN = Option[Double]
  type OUT = Option[BasicStatsResult]
  type INTER = BasicStatsAccum
}

object BasicStatsCalc extends Calculator[BasicStatsCalcTypePack] {

  override def fun(o: Unit) = { values: Traversable[IN] =>
    val flattenedValues = values.flatten
    if (flattenedValues.nonEmpty) {
      val count = flattenedValues.size

      val min = flattenedValues.min
      val max = flattenedValues.max
      val mean = flattenedValues.sum / count

      val meanSq = flattenedValues.foldLeft(0.0)((accum, value) => accum + value * value) / count
      val variance = meanSq - mean * mean
      val sVariance = sampleVariance(variance, count)

      Some(BasicStatsResult(min, max, mean, variance, Math.sqrt(variance), sVariance, Math.sqrt(sVariance), count, values.size - count))
    } else
      None
  }

  override def flow(o: Unit) =
    Flow[IN].fold[INTER](
      BasicStatsAccum(Double.MaxValue, Double.MinValue, 0, 0, 0, 0)
    ) {
      case (accum, value) => updateAccum(accum, value)
    }

  override def postFlow(o: Unit) = { accum: INTER =>
    if (accum.count > 0) {
      val mean = accum.sum / accum.count

      val meanSq = accum.sqSum / accum.count
      val variance = meanSq - mean * mean
      val sVariance = sampleVariance(variance, accum.count)

      Some(BasicStatsResult(
        accum.min, accum.max, mean, variance, Math.sqrt(variance), sVariance, Math.sqrt(sVariance), accum.count, accum.undefinedCount
      ))
    } else
      None
  }

  private def sampleVariance(variance: Double, count: Int) =
    if (count > 1) variance * count / (count - 1) else variance

  private[calc] def updateAccum(
    accum: BasicStatsAccum,
    value: Option[Double]
  ) =
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

case class BasicStatsResult(
  min: Double,
  max: Double,
  mean: Double,
  variance: Double,
  standardDeviation: Double,
  sampleVariance: Double,
  sampleStandardDeviation: Double,
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