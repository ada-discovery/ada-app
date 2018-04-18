package services.stats

import services.stats.calc._
import services.stats.CalculatorExecutor._

import scala.reflect.runtime.universe._

trait CalculatorExecutors {

  def uniqueDistributionCountsExec[T](
    implicit inputTypeTag: TypeTag[UniqueDistributionCountsCalcTypePack[T]#IN]
  ) = withSingle(UniqueDistributionCountsCalc[T])

  def groupUniqueDistributionCountsExec[G, T](
    implicit inputTypeTag: TypeTag[GroupUniqueDistributionCountsCalcTypePack[G, T]#IN]
  ) = with2Tuple(GroupUniqueDistributionCountsCalc[G, T])

  def numericDistributionCountsExec =
    withSingle(NumericDistributionCountsCalc.apply)

  def groupNumericDistributionCountsExec[G](
    implicit inputTypeTag: TypeTag[GroupNumericDistributionCountsCalcTypePack[G]#IN]
  ) = with2Tuple(GroupNumericDistributionCountsCalc[G])

  def cumulativeOrderedCountsExec[T: Ordering](
    implicit inputTypeTag: TypeTag[CumulativeOrderedCountsCalcTypePack[T]#IN]
  ) = withSingle(CumulativeOrderedCountsCalc[T])

  def cumulativeOrderedCountsAnyExec =
    CumulativeOrderedCountsAnyExec.apply

  def groupCumulativeOrderedCountsExec[G, T: Ordering](
    implicit inputTypeTag: TypeTag[GroupCumulativeOrderedCountsCalcTypePack[G, T]#IN]
  ) = with2Tuple(GroupCumulativeOrderedCountsCalc[G, T])

  def groupCumulativeOrderedCountsAnyExec[G](
    implicit inputTypeTag: TypeTag[GroupCumulativeOrderedCountsCalcTypePack[G, Any]#IN]
  ) = GroupCumulativeOrderedCountsAnyExec.apply[G]

  def basicStatsExec =
    withSingle(BasicStatsCalc)

  def tupleExec[A, B](
    implicit inputTypeTag: TypeTag[TupleCalcTypePack[A, B]#IN]
  ) =
    with2Tuple(TupleCalc.apply[A, B])

  def groupTupleExec[G, A, B](
    implicit inputTypeTag: TypeTag[GroupTupleCalcTypePack[G, A, B]#IN]
  ) =
    with3Tuple(GroupTupleCalc.apply[G, A, B])

  def quartilesExec[T: Ordering](
    implicit inputTypeTag: TypeTag[QuartilesCalcTypePack[T]#IN]
  ) = withSingle(QuartilesCalc[T])

  def pearsonCorrelationExec =
    withSeq(PearsonCorrelationCalc)

  def pearsonCorrelationAllDefinedExec =
    withSeq(AllDefinedPearsonCorrelationCalc)

  def euclideanDistanceExec =
    withSeq(EuclideanDistanceCalc)

  def euclideanDistanceAllDefinedExec =
    withSeq(AllDefinedEuclideanDistanceCalc)

  //  def quartilesExecAny = withSingle(QuartilesCalc.apply[Any])
}