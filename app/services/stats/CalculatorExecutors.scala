package services.stats

import services.stats.calc._
import services.stats.CalculatorExecutor._

import scala.reflect.runtime.universe._

trait CalculatorExecutors {

  // Unique distribution counts

  def uniqueDistributionCountsExec[T](
    implicit inputTypeTag: TypeTag[UniqueDistributionCountsCalcTypePack[T]#IN]
  ) = withSingle(UniqueDistributionCountsCalc[T])

  def uniqueDistributionCountsSeqExec[T](
    implicit inputTypeTag: TypeTag[UniqueDistributionCountsCalcTypePack[T]#IN]
  ) = withSeq(UniqueDistributionCountsCalc[T])

  def groupUniqueDistributionCountsExec[G, T](
    implicit inputTypeTag: TypeTag[GroupUniqueDistributionCountsCalcTypePack[G, T]#IN]
  ) = with2Tuple(GroupUniqueDistributionCountsCalc[G, T])

  def groupUniqueDistributionCountsSeqExec[G, T](
    implicit inputTypeTag: TypeTag[GroupUniqueDistributionCountsCalcTypePack[G, T]#IN]
  ) = withSeq(GroupUniqueDistributionCountsCalc[G, T])

  // Numeric distribution counts

  def numericDistributionCountsExec =
    withSingle(NumericDistributionCountsCalc.apply)

  def numericDistributionCountsSeqExec =
    withSeq(NumericDistributionCountsCalc.apply)

  def groupNumericDistributionCountsExec[G](
    implicit inputTypeTag: TypeTag[GroupNumericDistributionCountsCalcTypePack[G]#IN]
  ) = with2Tuple(GroupNumericDistributionCountsCalc[G])

  def groupNumericDistributionCountsSeqExec[G](
    implicit inputTypeTag: TypeTag[GroupNumericDistributionCountsCalcTypePack[G]#IN]
  ) = withSeq(GroupNumericDistributionCountsCalc[G])

  // Cumulative ordered counts

  def cumulativeOrderedCountsExec[T: Ordering](
    implicit inputTypeTag: TypeTag[CumulativeOrderedCountsCalcTypePack[T]#IN]
  ) = withSingle(CumulativeOrderedCountsCalc[T])

  def cumulativeOrderedCountsSeqExec[T: Ordering](
    implicit inputTypeTag: TypeTag[CumulativeOrderedCountsCalcTypePack[T]#IN]
  ) = withSeq(CumulativeOrderedCountsCalc[T])

  def cumulativeOrderedCountsAnyExec =
    CumulativeOrderedCountsAnyExec.withSingle

  def cumulativeOrderedCountsAnySeqExec =
    CumulativeOrderedCountsAnyExec.withSeq

  def groupCumulativeOrderedCountsExec[G, T: Ordering](
    implicit inputTypeTag: TypeTag[GroupCumulativeOrderedCountsCalcTypePack[G, T]#IN]
  ) = with2Tuple(GroupCumulativeOrderedCountsCalc[G, T])

  def groupCumulativeOrderedCountsSeqExec[G, T: Ordering](
    implicit inputTypeTag: TypeTag[GroupCumulativeOrderedCountsCalcTypePack[G, T]#IN]
  ) = withSeq(GroupCumulativeOrderedCountsCalc[G, T])

  def groupCumulativeOrderedCountsAnyExec[G](
    implicit inputTypeTag: TypeTag[GroupCumulativeOrderedCountsCalcTypePack[G, Any]#IN]
  ) = GroupCumulativeOrderedCountsAnyExec.with2Tuple[G]

  def groupCumulativeOrderedCountsAnySeqExec[G](
    implicit inputTypeTag: TypeTag[GroupCumulativeOrderedCountsCalcTypePack[G, Any]#IN]
  ) = GroupCumulativeOrderedCountsAnyExec.withSeq[G]

  // Cumulative numeric bin counts

  def cumulativeNumericBinCountsExec =
    withSingle(CumulativeNumericBinCountsCalc.apply)

  def cumulativeNumericBinCountsSeqExec =
    withSeq(CumulativeNumericBinCountsCalc.apply)

  def groupCumulativeNumericBinCountsExec[G](
    implicit inputTypeTag: TypeTag[GroupCumulativeNumericBinCountsCalcTypePack[G]#IN]
  ) = with2Tuple(GroupCumulativeNumericBinCountsCalc.apply[G])

  def groupCumulativeNumericBinCountsSeqExec[G](
    implicit inputTypeTag: TypeTag[GroupCumulativeNumericBinCountsCalcTypePack[G]#IN]
  ) = withSeq(GroupCumulativeNumericBinCountsCalc.apply[G])

  // Basic stats

  def basicStatsExec =
    withSingle(BasicStatsCalc)

  def basicStatsSeqExec =
    withSeq(BasicStatsCalc)

  // Tuples

  def tupleExec[A, B](
    implicit inputTypeTag: TypeTag[TupleCalcTypePack[A, B]#IN]
  ) =
    with2Tuple(TupleCalc.apply[A, B])

  def tupleSeqExec[A, B](
    implicit inputTypeTag: TypeTag[TupleCalcTypePack[A, B]#IN]
  ) =
    withSeq(TupleCalc.apply[A, B])

  def uniqueTupleExec[A, B](
    implicit inputTypeTag: TypeTag[TupleCalcTypePack[A, B]#IN]
  ) =
    with2Tuple(UniqueTupleCalc.apply[A, B])

  def uniqueTupleSeqExec[A, B](
    implicit inputTypeTag: TypeTag[TupleCalcTypePack[A, B]#IN]
  ) =
    withSeq(UniqueTupleCalc.apply[A, B])

  def groupTupleExec[G, A, B](
    implicit inputTypeTag: TypeTag[GroupTupleCalcTypePack[G, A, B]#IN]
  ) =
    with3Tuple(GroupTupleCalc.apply[G, A, B])

  def groupTupleSeqExec[G, A, B](
    implicit inputTypeTag: TypeTag[GroupTupleCalcTypePack[G, A, B]#IN]
  ) =
    withSeq(GroupTupleCalc.apply[G, A, B])

  def groupUniqueTupleExec[G, A, B](
    implicit inputTypeTag: TypeTag[GroupTupleCalcTypePack[G, A, B]#IN]
  ) =
    with3Tuple(GroupUniqueTupleCalc.apply[G, A, B])

  def groupUniqueTupleSeqExec[G, A, B](
    implicit inputTypeTag: TypeTag[GroupTupleCalcTypePack[G, A, B]#IN]
  ) =
    withSeq(GroupUniqueTupleCalc.apply[G, A, B])

  // Quartiles

  def quartilesExec[T: Ordering](
    implicit inputTypeTag: TypeTag[QuartilesCalcTypePack[T]#IN]
  ) = withSingle(QuartilesCalc[T])

  def quartilesSeqExec[T: Ordering](
    implicit inputTypeTag: TypeTag[QuartilesCalcTypePack[T]#IN]
  ) = withSeq(QuartilesCalc[T])

  def quartilesAnyExec =
    QuartilesAnyExec.withSingle

  def quartilesAnySeqExec =
    QuartilesAnyExec.withSeq

  // Pearson correlation

  def pearsonCorrelationExec =
    withSeq(PearsonCorrelationCalc)

  def pearsonCorrelationAllDefinedExec =
    withSeq(AllDefinedPearsonCorrelationCalc)

  def euclideanDistanceExec =
    withSeq(EuclideanDistanceCalc)

  def euclideanDistanceAllDefinedExec =
    withSeq(AllDefinedEuclideanDistanceCalc)
}