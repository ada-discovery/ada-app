package services.stats.calc

import services.stats.{Calculator, NoOptionsCalculatorTypePack}
import services.stats.CalculatorHelper._

trait MultiOneWayAnovaCalcTypePack[G] extends NoOptionsCalculatorTypePack{
  type IN = (Option[G], Seq[Option[Double]])
  type OUT = Seq[Option[OneWayAnovaResult]]
  type INTER = Traversable[(Option[G], Seq[BasicStatsAccum])]
}

private class MultiOneWayAnovaCalc[G] extends Calculator[MultiOneWayAnovaCalcTypePack[G]] with OneWayAnovaHelper {

  private val basicStatsCalc = GroupMultiBasicStatsCalc[G]

  override def fun(o: Unit) =
    basicStatsCalc.fun_.andThen(calcAux)

  override def flow(o: Unit) =
    basicStatsCalc.flow(())

  override def postFlow(o: Unit) =
    basicStatsCalc.postFlow_.andThen(calcAux)(_)

  private def calcAux(groupStats: GroupMultiBasicStatsCalcTypePack[G]#OUT) = {
    val elementsCount = if (groupStats.nonEmpty) groupStats.head._2.size else 0

    def calcAt(index: Int) = {
      val statsResults = groupStats.flatMap(_._2(index))
      val anovaInputs = statsResults.map (basicStatsResult =>
        OneWayAnovaStatsInputAux(basicStatsResult.sum, basicStatsResult.sqSum, basicStatsResult.definedCount)
      )

      calcAnovaStatsSafe(anovaInputs)
    }

    (0 until elementsCount).par.map(calcAt).toList
  }
}

object MultiOneWayAnovaCalc {
  def apply[G]: Calculator[MultiOneWayAnovaCalcTypePack[G]] = new MultiOneWayAnovaCalc[G]
}