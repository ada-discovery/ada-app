package services.stats.calc

import akka.stream.scaladsl.Flow
import services.stats.{Calculator, NoOptionsCalculatorTypePack}
import util.GroupMapList
import services.stats.CalculatorHelper._
import util.AkkaStreamUtil.seqFlow

trait GroupMultiBasicStatsCalcTypePack[G] extends NoOptionsCalculatorTypePack{
  type IN = (G, Seq[Option[Double]])
  type OUT = Traversable[(G, Seq[Option[BasicStatsResult]])]
  type INTER = Traversable[(G, Seq[BasicStatsAccum])]
}

private class GroupMultiBasicStatsCalc[G] extends Calculator[GroupMultiBasicStatsCalcTypePack[G]] with GroupBasicStatsHelper {

  private val maxGroups = Int.MaxValue
  private val basicStatsCalc = MultiBasicStatsCalc

  override def fun(o: Unit) =
    _.toGroupMap.map { case (group, values) => (group, basicStatsCalc.fun_(values)) }

  override def flow(o: Unit) = {
    val groupFlow = Flow[IN]
      .groupBy(maxGroups, _._1)
      .map { case (group, values) => group -> values.map(initAccum)}
      .reduce((l, r) ⇒ (l._1, l._2.zip(r._2).map((reduceAccums(_,_)).tupled)))
      .mergeSubstreams

    groupFlow.via(seqFlow)
  }

  override def postFlow(o: Unit) =
    _.map { case (group, accum) => (group, basicStatsCalc.postFlow_(accum)) }
}

object GroupMultiBasicStatsCalc {
  def apply[G]: Calculator[GroupMultiBasicStatsCalcTypePack[G]] = new GroupMultiBasicStatsCalc[G]
}