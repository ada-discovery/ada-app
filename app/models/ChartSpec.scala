package models

import _root_.util.BasicStats.Quantiles
import play.api.libs.json._

import scala.collection.mutable.{Map => MMap}
import scala.math.BigDecimal.RoundingMode
import models.ChartType

abstract class ChartSpec {
  val title: String
}

case class CategoricalChartSpec(
  title: String,
  showLabels: Boolean,
  showLegend: Boolean,
  data: Seq[DataPoint],
  chartType: ChartType.Value
) extends ChartSpec

case class NumericalChartSpec(
  title: String,
  data: Seq[(String, Int)],
  chartType: ChartType.Value
) extends ChartSpec

case class ColumnChartSpec(
  title: String,
  data: Seq[(String, Int)]
) extends ChartSpec

case class ScatterChartSpec(
  title: String,
  xAxisCaption: String,
  yAxisCaption: String,
  data: Seq[(String, String, Seq[Seq[Any]])],
  expHeight : Option[Int] = None
) extends ChartSpec

case class BoxChartSpec[T](
  title: String,
  data: Quantiles[T]
) extends ChartSpec

case class DataPoint(
  key: Option[String],
  value: Int,
  label: String
)

case class FieldChartSpec(fieldName: String, chartSpec : ChartSpec)

object ChartSpec {

  /**
    * Given the values the counts/frequencies are calculated.
    * Unique values will be used as labels for the pie chart.
    * Calculated fraction of a field is used as pie chart slice sizes.
    *
    * @param values Raw values.
    * @return CategoricalChartSpec object for use in view.
    */
  def categorical[T](
    values: Traversable[Option[T]],
    keyLabelMap: Option[Map[String, String]] = None,
    title: String,
    showLabels: Boolean,
    showLegend: Boolean,
    chartType: Option[ChartType.Value] = None
  ): CategoricalChartSpec = {
    val countMap = MMap[Option[T], Int]()
    values.foreach { value =>
      val count = countMap.getOrElse(value, 0)
      countMap.update(value, count + 1)
    }
    val data = countMap.toSeq.sortBy(_._2).map {
      case (key, count) => {
        val stringKey = key.map(_.toString)
        val keyOrEmpty = stringKey.getOrElse("")
        DataPoint(stringKey, count, keyLabelMap.map(_.getOrElse(keyOrEmpty, keyOrEmpty)).getOrElse(keyOrEmpty))
      }
    }
    CategoricalChartSpec(title, showLabels, showLegend, data, chartType.getOrElse(ChartType.Pie))
  }

  /**
    * Given raw items and field names, column chart properties are generated.
    * Non-defined optional values are auto-calculated.
    *
    * @param values Doubles.
    * @param fieldName Fields of interest.
    * @param columnCount Number of columns
    * @param explMin Optional max value for scaling of the columns.
    * @param explMax Optional min value for scaling of the columns.
    * @return ColumnChartSpec for us in view.
    */
  def numerical[T: Numeric](
    values: Traversable[T],
    fieldName: String,
    title: String,
    columnCount: Int,
    specialColumnForMax: Boolean = false,
    explMin: Option[T] = None,
    explMax: Option[T] = None,
    chartType: Option[ChartType.Value] = None,
    xAxisLabel: Option[BigDecimal => String] = None
  ): NumericalChartSpec = {
    val numeric = implicitly[Numeric[T]]

    val data = if (values.nonEmpty) {

      val doubles = values.map(numeric.toDouble)

      val max = BigDecimal(
        if (explMax.isDefined)
          numeric.toDouble(explMax.get)
        else
          doubles.max
      )

      val min = BigDecimal(
        if (explMin.isDefined)
          numeric.toDouble(explMin.get)
        else
          doubles.min
      )

      val stepSize: BigDecimal =
        if (min == max)
          0
        else if (specialColumnForMax)
          (max - min) / (columnCount - 1)
        else
          (max - min) / columnCount

      val countMap = MMap[Int, Int]()

      // initialize counts to zero
      (0 until columnCount).foreach { index =>
        countMap.update(index, 0)
      }
      doubles.map { value =>
        val bucketIndex =
          if (stepSize.equals(BigDecimal(0)))
            0
          else if (value == max)
            columnCount - 1
          else
            ((value - min) / stepSize).setScale(0, RoundingMode.FLOOR).toInt

        val count = countMap.get(bucketIndex).get
        countMap.update(bucketIndex, count + 1)
      }

      countMap.toSeq.sortBy(_._1).map { case (index, count) =>
        val xValue = min + (index * stepSize)
        val xLabel = xAxisLabel.map(_.apply(xValue)).getOrElse(xValue.toString)
        (xLabel, count)
      }
    } else
      Seq[(String, Int)]()

    NumericalChartSpec(title, data, chartType.getOrElse(ChartType.Column))
  }
}