package services.stats.calc

import services.stats.{Calculator, NoOptionsCalculatorTypePack}
import services.stats.CalculatorHelper._

trait MultiOneWayAnovaTestCalcTypePack[G] extends NoOptionsCalculatorTypePack{
  type IN = (G, Seq[Option[Double]])
  type OUT = Seq[Option[OneWayAnovaResult]]
  type INTER = Traversable[(G, Seq[BasicStatsAccum])]
}

private[stats] class MultiOneWayAnovaTestCalc[G] extends Calculator[MultiOneWayAnovaTestCalcTypePack[G]] with OneWayAnovaHelper {

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

object MultiOneWayAnovaTestCalc {
  def apply[G]: Calculator[MultiOneWayAnovaTestCalcTypePack[G]] = new MultiOneWayAnovaTestCalc[G]
}