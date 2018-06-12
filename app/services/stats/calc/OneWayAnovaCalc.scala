package services.stats.calc

import org.apache.commons.math3.special.Beta
import services.stats.{Calculator, NoOptionsCalculatorTypePack}
import services.stats.CalculatorHelper._

trait OneWayAnovaCalcTypePack[G] extends NoOptionsCalculatorTypePack{
  type IN = (Option[G], Option[Double])
  type OUT = Option[OneWayAnovaResult]
  type INTER = Traversable[(Option[G], BasicStatsAccum)]
}

private class OneWayAnovaCalc[G] extends Calculator[OneWayAnovaCalcTypePack[G]] with OneWayAnovaHelper {

  private val basicStatsCalc = GroupBasicStatsCalc[G]

  override def fun(o: Unit) = { values: Traversable[IN] =>
    val anovaInputs = basicStatsCalc.fun_(values).collect {
      case (_, Some(basicStatsResult)) =>
        OneWayAnovaStatsInputAux(basicStatsResult.sum, basicStatsResult.sqSum, basicStatsResult.definedCount)
    }

    calcAnovaStatsOptional(anovaInputs)
  }

  override def flow(o: Unit) = basicStatsCalc.flow(())

  override def postFlow(o: Unit) = { accums: INTER =>
    val anovaInputs = accums.map { case (_, accum) =>
      OneWayAnovaStatsInputAux(accum.sum, accum.sqSum, accum.count)
    }

    calcAnovaStatsOptional(anovaInputs)
  }
}

trait OneWayAnovaHelper {

  private val epsilon = 1E-300

  protected def calcAnovaStatsOptional(
    groups: Traversable[OneWayAnovaStatsInputAux]
  ): Option[OneWayAnovaResult] =
    if (groups.size > 1)
      Some(calcAnovaStats(groups))
    else
      None

  protected def calcAnovaStats(
    groups: Traversable[OneWayAnovaStatsInputAux]
  ): OneWayAnovaResult = {
    val resultAux =
      groups.foldLeft(OneWayAnovaResultAux()) { case (result, data) =>
        val ss = data.sqSum - ((data.sum * data.sum) / data.count)

        OneWayAnovaResultAux(
          result.sum + data.sum,
          result.sqSum + data.sqSum,
          result.count + data.count,
          result.dfwg + data.count - 1,
          result.sswg + ss
        )
      }

    val sst = resultAux.sqSum - ((resultAux.sum * resultAux.sum) / resultAux.count)

    // between group stats
    val dfbg = groups.size - 1
    val msbg = (sst - resultAux.sswg) / dfbg

    // within group stats
    val mswg = resultAux.sswg / resultAux.dfwg

    // F-value
    val FValue = msbg / mswg

    // p-value
    val pValue = if (FValue <= 0)
      1d
    else
      1d - Beta.regularizedBeta((dfbg * FValue) / (resultAux.dfwg + dfbg * FValue), 0.5 * dfbg, 0.5 * resultAux.dfwg, epsilon)

    OneWayAnovaResult(pValue, FValue, dfbg, resultAux.dfwg)
  }

  case class OneWayAnovaStatsInputAux(
    sum: Double,
    sqSum: Double,
    count: Int
  )

  case class OneWayAnovaResultAux(
    sum: Double = 0,
    sqSum: Double = 0,
    count: Double = 0,
    dfwg: Int = 0,
    sswg: Double = 0
  )
}

case class OneWayAnovaResult(
  pValue: Double,
  FValue: Double,
  dfbg: Int, // degree of freedom between groups, degree of freedom in numerator
  dfwg: Int // degree of freedom within groups, degree of freedom in denominator
)

object OneWayAnovaCalc {
  def apply[G]: Calculator[OneWayAnovaCalcTypePack[G]] = new OneWayAnovaCalc[G]
}