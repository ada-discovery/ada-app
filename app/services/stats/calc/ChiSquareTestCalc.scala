package services.stats.calc

import org.apache.commons.math3.exception.MaxCountExceededException
import play.api.Logger
import services.stats.{Calculator, CommonsMathUtil, NoOptionsCalculatorTypePack}
import util.AkkaStreamUtil.{groupCountFlow, seqFlow}
import Seq.fill

trait ChiSquareTestCalcTypePack[T1, T2] extends NoOptionsCalculatorTypePack{
  type IN = (Option[T1], Option[T2])
  type OUT = Option[ChiSquareResult]
  type INTER = Traversable[((Option[T1], Option[T2]), Int)]
}

private class ChiSquareTestCalc[T1, T2] extends Calculator[ChiSquareTestCalcTypePack[T1, T2]] {

  private val epsilon = 1E-100
  private val logger = Logger

  override def fun(o: Unit) = { values: Traversable[IN] =>
    val countsMap = values.groupBy(identity).map {
      case (group, values) => (group, values.size)
    }
    calcChiSquareSafe(countsMap)
  }

  override def flow(o: Unit) =
    groupCountFlow[IN]().via(seqFlow)

  override def postFlow(o: Unit) = { values =>
    calcChiSquareSafe(values.toMap)
  }

  private def calcChiSquareSafe(
    countsMap: Map[IN, Int]
  ) = {
    val values1 = countsMap.map(_._1._1).toSet.toSeq
    val values2 = countsMap.map(_._1._2).toSet.toSeq

    val counts = values1.map( value1 =>
      values2.map( value2 => countsMap.get((value1, value2)).getOrElse(0) )
    )

    if (values1.size < 2 || values2.size < 2) {
      Logger.warn(s"Not enough values to perform Chi-Square test for: ${values1.size} x ${values2.size} counts.")
      None
    } else
      try {
        calcChiSquare(counts)
      } catch {
        case _: MaxCountExceededException =>
          Logger.warn(s"Max number of iterations reached for a Chi-Square test.")
          None
      }
  }

  private def calcChiSquare(
    counts: Seq[Seq[Int]]
  ): Option[ChiSquareResult] = {
    val stats = chiSquareStatistics(counts)

    val df = (counts.length.toDouble - 1) * (counts(0).length.toDouble - 1)
    val shape = df / 2
    val scale = 2

    def result(pValue: BigDecimal) =
      ChiSquareResult(pValue.doubleValue, stats, df.toInt)

    if (stats <= 0)
      Some(result(1d))
    else {
      val gamma = CommonsMathUtil.regularizedGammaP(shape, stats / scale, epsilon, Int.MaxValue)
      gamma.map(gamma => result(1d - gamma))
    }
  }

  private def chiSquareStatistics(
    counts: Seq[Seq[Int]]
  ): Double = {
    val colsNum = counts(0).size

    // row sums, column sums, and total sum
    val rowSums = counts.map(_.sum)
    val colSums = (0 to colsNum-1).map(col => counts.map(_(col)).sum)
    val total = rowSums.sum.toDouble

    // combine together and get a sum of value/expected squares
    counts.zipWithIndex.flatMap { case (row, rowIndex) =>
      row.zipWithIndex.map { case (value, colIndex) =>
        val expected: Double = (rowSums(rowIndex) * colSums(colIndex)) / total
        ((value - expected) * (value - expected)) / expected
      }
    }.sum
  }
}

case class ChiSquareResult(
  pValue: Double,
  statistics: Double,
  degreeOfFreedom: Int
)

object ChiSquareTestCalc {
  def apply[T1, T2]: Calculator[ChiSquareTestCalcTypePack[T1, T2]] = new ChiSquareTestCalc[T1, T2]
}