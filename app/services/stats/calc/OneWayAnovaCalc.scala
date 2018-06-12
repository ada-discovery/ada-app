package services.stats.calc

import services.stats.{Calculator, NoOptionsCalculatorTypePack}
import services.stats.CalculatorHelper._

trait OneWayAnovaCalcTypePack[G] extends NoOptionsCalculatorTypePack{
  type IN = (Option[G], Option[Double])
  type OUT = OneWayAnovaResult
  type INTER = Traversable[(Option[G], BasicStatsAccum)]
}

class OneWayAnovaCalc[G] extends Calculator[OneWayAnovaCalcTypePack[G]] {

  private val basicStatsCalc = GroupBasicStatsCalc[G]

  override def fun(o: Unit) = { values: Traversable[IN] =>

    val anovaInputs = basicStatsCalc.fun_(values).collect {
      case (_, Some(basicStatsResult)) => OneWayAnovaInputAux(basicStatsResult.sum, basicStatsResult.sqSum, basicStatsResult.definedCount)
    }

    anovaStats(anovaInputs)
  }

  override def flow(o: Unit) = basicStatsCalc.flow(())

  override def postFlow(o: Unit) = { accums: INTER =>
    val anovaInputs = accums.map { case (_, accum) =>
      OneWayAnovaInputAux(accum.sum, accum.sqSum, accum.count)
    }

    anovaStats(anovaInputs)
  }

  private def anovaStats(categoryData: Traversable[OneWayAnovaInputAux], allowOneElementData: Boolean = true) = {
//    if (!allowOneElementData) {
//      // check if we have enough categories
//      if (categoryData.size < 2) throw new DimensionMismatchException(LocalizedFormats.TWO_OR_MORE_CATEGORIES_REQUIRED, categoryData.size, 2)
//      // check if each category has enough data
//      import scala.collection.JavaConversions._
//      for (array <- categoryData) {
//        if (array.getN <= 1) throw new DimensionMismatchException(LocalizedFormats.TWO_OR_MORE_VALUES_IN_CATEGORY_REQUIRED, array.getN.toInt, 2)
//      }
//    }

    var dfwg = 0
    var sswg = 0d
    var totsum = 0d
    var totsumsq = 0d
    var totnum = 0

    for (data <- categoryData) {
      val sum = data.sum
      val sumsq = data.sqSum
      val num = data.count

      totnum += num
      totsum += sum
      totsumsq += sumsq

      dfwg += num - 1

      val ss = sumsq - ((sum * sum) / num)
      sswg += ss
    }

    val sst = totsumsq - ((totsum * totsum) / totnum)
    val ssbg = sst - sswg
    val dfbg = categoryData.size - 1
    val msbg = ssbg / dfbg
    val mswg = sswg / dfwg
    val F = msbg / mswg

    OneWayAnovaResult(1.1d, F, dfbg, dfwg)
  }

  case class OneWayAnovaInputAux(
    sum: Double,
    sqSum: Double,
    count: Int
  )
}

case class OneWayAnovaResult(
  pValue: Double,
  FValue: Double,
  degreeOfFreedomNumerator: Int, // degreeoffreedom between groups
  degreeOfFreedomDenominator: Int // degreeoffreedom within groups
)