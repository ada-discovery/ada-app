package services.stats.calc

import services.stats.{Calculator, NoOptionsCalculatorTypePack}

trait GroupCumulativeOrderedCountsCalcTypePack[G, T] extends NoOptionsCalculatorTypePack {
  type IN = (Option[G], Option[T])
  type OUT = Traversable[(Option[G], Traversable[(T, Int)])]
  type INTER = Traversable[((Option[G], Option[T]), Int)]
}

private class GroupCumulativeOrderedCountsCalc[G, T: Ordering] extends Calculator[GroupCumulativeOrderedCountsCalcTypePack[G, T]] with CumulativeOrderedCountsCalcFun {

  private val basicCalc = GroupUniqueDistributionCountsCalc.apply[G, T]

  override def fun(options: Unit) =
    (basicCalc.fun(options)(_)) andThen groupSortAndCount

  override def flow(options: Unit) =
    basicCalc.flow(options)

  override def postFlow(options: Unit) =
    (basicCalc.postFlow(options)(_)) andThen groupSortAndCount

  private def groupSortAndCount(groupValues: GroupUniqueDistributionCountsCalcTypePack[G, T]#OUT) =
    groupValues.map { case (group, values) => (group, sortAndCount(values)) }
}

object GroupCumulativeOrderedCountsCalc {
  def apply[G, T: Ordering]: Calculator[GroupCumulativeOrderedCountsCalcTypePack[G, T]] = new GroupCumulativeOrderedCountsCalc[G, T]
}