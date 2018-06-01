package services.stats.calc

import akka.stream.scaladsl.Flow
import org.apache.commons.math3.exception.{DimensionMismatchException, NullArgumentException}
import services.stats.{Calculator, NoOptionsCalculatorTypePack}

trait OneWayAnovaCalcTypePack[G] extends NoOptionsCalculatorTypePack{
  type IN = (Option[G], Option[Double])
  type OUT = Traversable[(Option[G], Option[OneWayAnovaResult])]
  type INTER = Traversable[(Option[G], OneWayAnovaAccum)]
}

//class OneWayAnovaCalc[G] extends Calculator[OneWayAnovaCalcTypePack[G]] {
//
//  override def fun(o: Unit) = { values: Traversable[IN] =>
//    val flattenedValues = values.flatten
//    if (flattenedValues.nonEmpty) {
//      val count = flattenedValues.size
//
//      val min = flattenedValues.min
//      val max = flattenedValues.max
//      val mean = flattenedValues.sum / count
//
//      val meanSq = flattenedValues.foldLeft(0.0)((accum, value) => accum + value * value) / count
//      val variance = meanSq - mean * mean
//      val sVariance = sampleVariance(variance, count)
//
//      Some(OneWayAnovaResult(min, max, mean, variance, Math.sqrt(variance), sVariance, Math.sqrt(sVariance), count, values.size - count))
//    } else
//      None
//  }
//
//  override def flow(o: Unit) =
//    Flow[IN].fold[INTER](
//      OneWayAnovaAccum(Double.MaxValue, Double.MinValue, 0, 0, 0, 0)
//    ) {
//      case (accum, value) => updateAccum(accum, value)
//    }
//
//  override def postFlow(o: Unit) = { accum: INTER =>
//    if (accum.count > 0) {
//      val mean = accum.sum / accum.count
//
//      val meanSq = accum.sqSum / accum.count
//      val variance = meanSq - mean * mean
//      val sVariance = sampleVariance(variance, accum.count)
//
//      Some(OneWayAnovaResult(
//        accum.min, accum.max, mean, variance, Math.sqrt(variance), sVariance, Math.sqrt(sVariance), accum.count, accum.undefinedCount
//      ))
//    } else
//      None
//  }
//
//  private def sampleVariance(variance: Double, count: Int) =
//    if (count > 1) variance * count / (count - 1) else variance
//
//  private[calc] def updateAccum(
//    accum: OneWayAnovaAccum,
//    value: Option[Double]
//  ) =
//    value match {
//      case Some(value) =>
//        OneWayAnovaAccum(
//          Math.min(accum.min, value),
//          Math.max(accum.max, value),
//          accum.sum + value,
//          accum.sqSum + value * value,
//          accum.count + 1,
//          accum.undefinedCount
//        )
//
//      case None => accum.copy(undefinedCount = accum.undefinedCount + 1)
//    }
//
//  private def anovaStats(categoryData: Seq[OneWayAnovaAccum], allowOneElementData: Boolean) = {
////    if (!allowOneElementData) {
////      // check if we have enough categories
////      if (categoryData.size < 2) throw new DimensionMismatchException(LocalizedFormats.TWO_OR_MORE_CATEGORIES_REQUIRED, categoryData.size, 2)
////      // check if each category has enough data
////      import scala.collection.JavaConversions._
////      for (array <- categoryData) {
////        if (array.getN <= 1) throw new DimensionMismatchException(LocalizedFormats.TWO_OR_MORE_VALUES_IN_CATEGORY_REQUIRED, array.getN.toInt, 2)
////      }
////    }
//
//    var dfwg = 0
//    var sswg = 0
//    var totsum = 0
//    var totsumsq = 0
//    var totnum = 0
//
//    for (data <- categoryData) {
//      val sum = data.sum
//      val sumsq = data.sqSum
//      val num = data.count
//      totnum += num
//      totsum += sum
//      totsumsq += sumsq
//
//      dfwg += num - 1
//
//      val ss = sumsq - ((sum * sum) / num)
//      sswg += ss
//    }
//
//    val sst = totsumsq - ((totsum * totsum) / totnum)
//    val ssbg = sst - sswg
//    val dfbg = categoryData.size - 1
//    val msbg = ssbg / dfbg
//    val mswg = sswg / dfwg
//    val F = msbg / mswg
//
//    OneWayAnovaResult(1.1d, F, dfbg, dfwg)
//  }
//}
//
case class OneWayAnovaResult(
  pValue: Double,
  FValue: Double,
  degreeOfFreedomNumerator: Int, // degreeoffreedom between groups
  degreeOfFreedomDenominator: Int // degreeoffreedom within groups
)

case class OneWayAnovaAccum(
  sum: Double,
  sqSum: Double,
  count: Int
)