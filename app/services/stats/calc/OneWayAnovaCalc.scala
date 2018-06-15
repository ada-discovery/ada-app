package services.stats.calc

import java.{lang => jl}

import org.apache.commons.math3.exception.MaxCountExceededException
import org.apache.commons.math3.special.Beta
import org.apache.commons.math3.util.{ContinuedFraction, FastMath}
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

    calcAnovaStatsSafe(anovaInputs)
  }

  override def flow(o: Unit) = basicStatsCalc.flow(())

  override def postFlow(o: Unit) = { accums: INTER =>
    val anovaInputs = accums.map { case (_, accum) =>
      OneWayAnovaStatsInputAux(accum.sum, accum.sqSum, accum.count)
    }

    calcAnovaStatsSafe(anovaInputs)
  }
}

trait OneWayAnovaHelper {

  private val epsilon = 1E-100

  protected def calcAnovaStatsSafe(
    groups: Traversable[OneWayAnovaStatsInputAux]
  ): Option[OneWayAnovaResult] = {
    val totalCount = groups.map(_.count).sum

    if (groups.size > 1 && (totalCount - groups.size) > 0)
      try {
        calcAnovaStats(groups)
      } catch {
        case _: MaxCountExceededException => None
      }
    else
      None
  }

  private def calcAnovaStats(
    groups: Traversable[OneWayAnovaStatsInputAux]
  ): Option[OneWayAnovaResult] = {
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

    def result(pValue: Double) = OneWayAnovaResult(pValue, FValue, dfbg, resultAux.dfwg)

    // p-value
    if (FValue <= 0)
      Some(result(1d))
    else {
      val beta = regularizedBeta((dfbg * FValue) / (resultAux.dfwg + dfbg * FValue), 0.5 * dfbg, 0.5 * resultAux.dfwg, epsilon, Integer.MAX_VALUE)
      beta.map(beta => result((1d - beta).doubleValue()))
    }
  }

  /**
    * This is a Scala version with an optimized precision of the <code>org.apache.commons.math3.special.Beta.regularizedBeta</code> function
    */
  def regularizedBeta(
    x: Double,
    a: Double,
    b: Double,
    epsilon: Double,
    maxIterations: Int
  ): Option[BigDecimal] =
    if (jl.Double.isNaN(x) || jl.Double.isNaN(a) || jl.Double.isNaN(b) || x < 0 || x > 1 || a <= 0 || b <= 0)
      None
    else if (x > (a + 1) / (2 + b + a) && 1 - x <= (b + 1) / (2 + b + a))
      regularizedBeta(1 - x, b, a, epsilon, maxIterations).map ( beta =>
        1d - beta
      )
    else {
      val fraction = new ContinuedFraction() {

        protected def getB(n: Int, x: Double): Double = {
          var ret = .0
          var m = .0
          if (n % 2 == 0) {
            // even
            m = n / 2.0
            ret = (m * (b - m) * x) / ((a + (2 * m) - 1) * (a + (2 * m)))
          }
          else {
            m = (n - 1.0) / 2.0
            ret = -((a + m) * (a + b + m) * x) / ((a + (2 * m)) * (a + (2 * m) + 1.0))
          }
          ret
        }

        protected def getA(n: Int, x: Double) = 1.0
      }
      val result: BigDecimal = FastMath.exp((a * FastMath.log(x)) + (b * FastMath.log1p(-x)) - FastMath.log(a) - Beta.logBeta(a, b)) * 1.0 / fraction.evaluate(x, epsilon, maxIterations)
      Some(result)
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