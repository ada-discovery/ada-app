package services.stats.calc

import services.stats.NoOptionsCalculator
import MatrixRowColumnSumCalcIOTypes._

object MatrixRowColumnMeanCalc extends NoOptionsCalculator[IN, OUT, INTER] {

  private val sumCalc = MatrixRowColumnSumCalc

  override def fun(o: Unit) = { values: Traversable[IN] =>
    val n = values.size
    val (rowSums, columnSums) = sumCalc.fun()(values)
    val rowMeans = rowSums.map(_ / n)
    val columnMeans = columnSums.map(_ / n)

    (rowMeans, columnMeans)
  }

  override def flow(o: Unit) = sumCalc.flow()

  override def postFlow(o: Unit) = { case (rowSums, columnSums) =>
    val n = rowSums.size
    val rowMeans = rowSums.map(_ / n)
    val columnMeans = columnSums.map(_ / n)

    (rowMeans, columnMeans)
  }
}