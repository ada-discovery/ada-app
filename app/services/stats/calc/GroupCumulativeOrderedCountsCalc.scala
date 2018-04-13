package services.stats.calc

import services.stats.CalculatorHelper.NoOptionsCalculator
import services.stats.calc.GroupCumulativeOrderedCountsCalcIOTypes._

object GroupCumulativeOrderedCountsCalcIOTypes {
  type IN[G, T] = (Option[G], Option[T])
  type OUT[G, T] = Traversable[(Option[G], Traversable[(T, Int)])]
  type INTER[G, T] = Traversable[((Option[G], Option[T]), Int)]
}

private class GroupCumulativeOrderedCountsCalc[G, T: Ordering] extends NoOptionsCalculator[IN[G,T], OUT[G,T], INTER[G,T]] with CumulativeOrderedCountsCalcFun {

  private val basicCalc = GroupUniqueDistributionCountsCalc[G, T]

  override def fun(options: Unit) =
    (basicCalc.fun()(_)) andThen groupSortAndCount

  override def flow(options: Unit) =
    basicCalc.flow()

  override def postFlow(options: Unit) =
    (basicCalc.postFlow()(_)) andThen groupSortAndCount

  private def groupSortAndCount(groupValues: GroupUniqueDistributionCountsCalcIOTypes.OUT[G, T]) =
    groupValues.map { case (group, values) => (group, sortAndCount(values)) }
}

object GroupCumulativeOrderedCountsCalc {
  def apply[G, T: Ordering]: NoOptionsCalculator[IN[G,T], OUT[G,T], INTER[G,T]] = new GroupCumulativeOrderedCountsCalc[G, T]
}