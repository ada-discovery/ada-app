package services.stats.calc

import akka.stream.scaladsl.Sink
import services.stats.Calculator

import scala.collection.mutable.{Map => MMap}
import services.stats.calc.NumericalDistributionCountsCalcIOType._

import scala.collection.mutable
import scala.math.BigDecimal.RoundingMode

object NumericalDistributionCountsCalcIOType {
  type IN[T] = T
  type OUT[T] = Traversable[(BigDecimal, Int)]
  type INTER[T] = mutable.ArraySeq[Int]
  type OPTIONS[T] = NumericalDistributionOptions[T]
  type SINK_OPTIONS[T] = NumericalDistributionSinkOptions[T]
}

private class NumericalDistributionCountsCalc[T: Numeric] extends Calculator[IN[T], OUT[T], INTER[T], OPTIONS[T], SINK_OPTIONS[T], SINK_OPTIONS[T]] {

  private val numeric = implicitly[Numeric[T]]

  override def fun(options: NumericalDistributionOptions[T]) = { values =>
    if (values.nonEmpty) {
      val min = options.min.getOrElse(values.min)
      val max = options.max.getOrElse(values.max)
      val minBg = BigDecimal(numeric.toDouble(min))

      val stepSize = calcStepSize(options.columnCount, min, max, options.specialColumnForMax)

      val doubles = values.map(numeric.toDouble)

      val bucketIndeces = doubles.map { value =>
        if (stepSize.equals(BigDecimal(0)))
          0
        else if (value == max)
          options.columnCount - 1
        else
          ((value - minBg) / stepSize).setScale(0, RoundingMode.FLOOR).toInt
      }

      val countMap = bucketIndeces.groupBy(identity).map { case (index, values) => (index, values.size) }

      (0 until options.columnCount).map { index =>
        val count = countMap.get(index).getOrElse(0)
        val xValue = minBg + (index * stepSize)
        (xValue, count)
      }
    } else
      Seq[(BigDecimal, Int)]()
  }

  override def sink(options: SINK_OPTIONS[T]) = {
    val stepSize = calcStepSize(
      options.columnCount,
      options.min,
      options.max,
      options.specialColumnForMax
    )

    val minBg = BigDecimal(numeric.toDouble(options.min))

    Sink.fold[INTER[T], IN[T]](
      mutable.ArraySeq(Seq.fill(0)(options.columnCount) :_*)
    ) { case (array, value) =>
      val doubleValue = numeric.toDouble(value)

      val index =
        if (stepSize.equals(BigDecimal(0)))
          0
        else if (doubleValue == options.max)
          options.columnCount - 1
        else
          ((doubleValue - minBg) / stepSize).setScale(0, RoundingMode.FLOOR).toInt

      array.update(index, array(index) + 1)
      array
    }
  }

  private def calcStepSize(
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

  override def postSink(options: SINK_OPTIONS[T]) = { array =>
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

object NumericalDistributionCountsCalc {
  def apply[T: Numeric]: Calculator[IN[T], OUT[T], INTER[T], OPTIONS[T], SINK_OPTIONS[T], SINK_OPTIONS[T]] = new NumericalDistributionCountsCalc[T]
}

case class NumericalDistributionOptions[T](
  columnCount: Int,
  min: Option[T] = None,
  max: Option[T] = None,
  specialColumnForMax: Boolean = false
)

case class NumericalDistributionSinkOptions[T](
  columnCount: Int,
  min: T,
  max: T,
  specialColumnForMax: Boolean = false
)