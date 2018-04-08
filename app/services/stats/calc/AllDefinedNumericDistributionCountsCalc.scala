package services.stats.calc

import akka.stream.scaladsl.{Flow, Sink}
import services.stats.Calculator
import services.stats.calc.AllDefinedNumericDistributionCountsCalcIOType._

import scala.collection.mutable
import scala.math.BigDecimal.RoundingMode

object AllDefinedNumericDistributionCountsCalcIOType {
  type IN[T] = T
  type OUT[T] = Traversable[(BigDecimal, Int)]
  type INTER[T] = mutable.ArraySeq[Int]
  type OPTIONS[T] = NumericDistributionOptions[T]
  type SINK_OPTIONS[T] = NumericDistributionSinkOptions[T]
}

private class AllDefinedNumericDistributionCountsCalc[T: Numeric] extends Calculator[IN[T], OUT[T], INTER[T], OPTIONS[T], SINK_OPTIONS[T], SINK_OPTIONS[T]]
  with NumericDistributionCountsHelper[T] {

  override val numeric = implicitly[Numeric[T]]
  private val zero = BigDecimal(0)

  override def fun(options: NumericDistributionOptions[T]) = { values =>
    if (values.nonEmpty) {
      val min = options.min.getOrElse(values.min)
      val max = options.max.getOrElse(values.max)

      val stepSize = calcStepSize(options.columnCount, min, max, options.specialColumnForMax)

      val minBg = BigDecimal(numeric.toDouble(min))
      val maxDouble = numeric.toDouble(max)

      val bucketIndeces = values.map(numeric.toDouble).map(
        calcBucketIndex(stepSize, options.columnCount, minBg, maxDouble)
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

  override def flow(options: SINK_OPTIONS[T]) = {
    val stepSize = calcStepSize(
      options.columnCount,
      options.min,
      options.max,
      options.specialColumnForMax
    )

    val minBg = BigDecimal(numeric.toDouble(options.min))
    val max = numeric.toDouble(options.max)

    Flow[IN[T]].fold[INTER[T]](
      mutable.ArraySeq(Seq.fill(options.columnCount)(0) :_*)
    ) { case (array, value) =>
      val index = calcBucketIndex(
        stepSize, options.columnCount, minBg, max)(
        numeric.toDouble(value)
      )
      array.update(index, array(index) + 1)
      array
    }
  }

  override def postFlow(options: SINK_OPTIONS[T]) = { array =>
    val columnCount = array.length

    val stepSize = calcStepSize(
      options.columnCount,
      options.min,
      options.max,
      options.specialColumnForMax
    )

    val minBg = BigDecimal(numeric.toDouble(options.min))

    (0 until columnCount).map { index =>
      val count = array(index)
      val xValue = minBg + (index * stepSize)
      (xValue, count)
    }
  }
}

object AllDefinedNumericDistributionCountsCalc {
  def apply[T: Numeric]: Calculator[IN[T], OUT[T], INTER[T], OPTIONS[T], SINK_OPTIONS[T], SINK_OPTIONS[T]] = new AllDefinedNumericDistributionCountsCalc[T]
}

trait NumericDistributionCountsHelper[T] {

  private val zero = BigDecimal(0)

  protected def numeric: Numeric[T]

  def calcStepSize(
    columnCount: Int,
    min: T,
    max: T,
    specialColumnForMax: Boolean
  ): BigDecimal = {
    val minBd = BigDecimal(numeric.toDouble(min))
    val maxBd = BigDecimal(numeric.toDouble(max))

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

case class NumericDistributionOptions[T](
  columnCount: Int,
  min: Option[T] = None,
  max: Option[T] = None,
  specialColumnForMax: Boolean = false
)

case class NumericDistributionSinkOptions[T](
  columnCount: Int,
  min: T,
  max: T,
  specialColumnForMax: Boolean = false
)