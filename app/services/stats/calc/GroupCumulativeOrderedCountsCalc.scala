package services.stats.calc

import akka.NotUsed
import akka.stream.scaladsl.Flow
import services.stats.{Calculator, NoOptionsCalculatorTypePack}

trait GroupCumulativeOrderedCountsCalcTypePack[G, T] extends NoOptionsCalculatorTypePack {
  type IN = (Option[G], Option[T])
  type OUT = Traversable[(Option[G], Traversable[(T, Int)])]
  type INTER = Traversable[((Option[G], Option[T]), Int)]
}

private class GroupCumulativeOrderedCountsCalc[G, T: Ordering] extends Calculator[GroupCumulativeOrderedCountsCalcTypePack[G, T]] with CumulativeOrderedCountsCalcFun {

  private val basicCalc = GroupUniqueDistributionCountsCalc.apply[G, T]

  override def fun(options: Unit): Traversable[IN] => OUT =
    (basicCalc.fun(())(_)) andThen groupSortAndCount
//    (inputs: Traversable[IN]) => {
//      val outputs = basicCalc.fun(())(inputs)
//      groupSortAndCount(outputs)
//    }

  override def flow(options: Unit): Flow[IN, INTER, NotUsed] =
    basicCalc.flow(())

  override def postFlow(options: Unit): INTER => OUT =
    (basicCalc.postFlow(())(_)) andThen groupSortAndCount

  private def groupSortAndCount(groupValues: GroupUniqueDistributionCountsCalcTypePack[G, T]#OUT) =
    groupValues.map { case (group, values) => (group, sortAndCount(values)) }
}

object GroupCumulativeOrderedCountsCalc {
  def apply[G, T: Ordering]: Calculator[GroupCumulativeOrderedCountsCalcTypePack[G, T]] = new GroupCumulativeOrderedCountsCalc[G, T]
}