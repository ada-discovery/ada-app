package services.stats.calc

import akka.stream.scaladsl.Flow
import services.stats.{Calculator, CalculatorTypePack}

import scala.collection.mutable
import scala.math.BigDecimal.RoundingMode

trait AllDefinedNumericDistributionCountsCalcTypePack extends CalculatorTypePack {
  type IN = Double
  type OUT = Traversable[(BigDecimal, Int)]
  type INTER = mutable.ArraySeq[Int]
  type OPT = NumericDistributionOptions
  type FLOW_OPT = NumericDistributionFlowOptions
  type SINK_OPT = FLOW_OPT
}

private class AllDefinedNumericDistributionCountsCalc extends Calculator[AllDefinedNumericDistributionCountsCalcTypePack] with NumericDistributionCountsHelper {

  private val zero = BigDecimal(0)

  override def fun(options: NumericDistributionOptions) = { values =>
    if (values.nonEmpty) {
      val min = values.min
      val max = values.max

      val stepSize = calcStepSize(options.columnCount, min, max, options.specialColumnForMax)

      val minBg = BigDecimal(min)

      val bucketIndeces = values.map(
        calcBucketIndex(stepSize, options.columnCount, minBg, max)
      )

      val countMap = bucketIndeces.groupBy(identity).map { case (index, values) => (index, values.size) }

      (0 until options.columnCount).map { index =>
        val count = countMap.get(index).getOrElse(0)
        val xValue = minBg + (index * stepSize)
        (xValue, count)
      }
    } else
      Seq[(BigDecimal, Int)]()
  }

  override def flow(options: FLOW_OPT) = {
    val stepSize = calcStepSize(
      options.columnCount,
      options.min,
      options.max,
      options.specialColumnForMax
    )

    val minBg = BigDecimal(options.min)
    val max = options.max

    Flow[IN].fold[INTER](
      mutable.ArraySeq(Seq.fill(options.columnCount)(0) :_*)
    ) { case (array, value) =>
      val index = calcBucketIndex(
        stepSize, options.columnCount, minBg, max)(value)
      array.update(index, array(index) + 1)
      array
    }
  }

  override def postFlow(options: SINK_OPT) = { array =>
    val columnCount = array.length

    val stepSize = calcStepSize(
      options.columnCount,
      options.min,
      options.max,
      options.specialColumnForMax
    )

    val minBg = BigDecimal(options.min)

    (0 until columnCount).map { index =>
      val count = array(index)
      val xValue = minBg + (index * stepSize)
      (xValue, count)
    }
  }
}

object AllDefinedNumericDistributionCountsCalc {
  def apply: Calculator[AllDefinedNumericDistributionCountsCalcTypePack] = new AllDefinedNumericDistributionCountsCalc
}

trait NumericDistributionCountsHelper {

  private val zero = BigDecimal(0)

  def calcStepSize(
    columnCount: Int,
    min: Double,
    max: Double,
    specialColumnForMax: Boolean
  ): BigDecimal = {
    val minBd = BigDecimal(min)
    val maxBd = BigDecimal(max)

    if (minBd >= maxBd)
      0
    else if (specialColumnForMax)
      (maxBd - minBd) / (columnCount - 1)
    else
      (maxBd - minBd) / columnCount
  }

  def calcBucketIndex(
    stepSize: BigDecimal,
    columnCount: Int,
    minBg: BigDecimal,
    max: Double)(
    doubleValue: Double
  ) =
    if (stepSize.equals(zero))
      0
    else if (doubleValue == max)
      columnCount - 1
    else
      ((doubleValue - minBg) / stepSize).setScale(0, RoundingMode.FLOOR).toInt
}

case class NumericDistributionOptions(
  columnCount: Int,
  specialColumnForMax: Boolean = false
)

case class NumericDistributionFlowOptions(
  columnCount: Int,
  min: Double,
  max: Double,
  specialColumnForMax: Boolean = false
)