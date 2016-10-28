package models

import play.api.libs.json._

import scala.collection.mutable.{Map => MMap}
import scala.math.BigDecimal.RoundingMode
import dataaccess.ChartType

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
  data: Seq[(String, String, Seq[Seq[Any]])]
) extends ChartSpec

case class BoxPlotSpec(
  title: String,
  data: Seq[(String, Seq[Double])]
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
    new CategoricalChartSpec(title, showLabels, showLegend, data, chartType.getOrElse(ChartType.Pie))
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
    xAxisScale: Option[Int] = None,
    explMin: Option[T] = None,
    explMax: Option[T] = None,
    chartType: Option[ChartType.Value] = None,
    xAxisLabel: Option[BigDecimal => String] = None
  ): NumericalChartSpec = {
    val numeric = implicitly[Numeric[T]]

    val data = if (values.nonEmpty) {
      val max = BigDecimal(
        numeric.toDouble {
          if (explMax.isDefined) explMax.get else values.reduce(numeric.max)
        })

      val min = BigDecimal(
        numeric.toDouble {
          if (explMin.isDefined) explMin.get else values.reduce(numeric.min)
        })

      val stepSize: BigDecimal = (max - min) / columnCount

      for (step <- 0 until columnCount) yield {
        val left = min + step * stepSize
        val right = left + stepSize
        val count = if (step == columnCount - 1) {
          values.filter(value => numeric.toDouble(value) >= left && numeric.toDouble(value) <= right).size
        } else {
          values.filter(value => numeric.toDouble(value) >= left && numeric.toDouble(value) < right).size
        }

        val xValue = if (xAxisScale.isDefined)
          left.setScale(xAxisScale.get, RoundingMode.HALF_UP)
        else
          left

        (xAxisLabel.map(_.apply(xValue)).getOrElse(xValue.toString), count)
      }
    } else
      Seq[(String, Int)]()

    NumericalChartSpec(title, data, chartType.getOrElse(ChartType.Column))
  }

  /**
    * Exctracts fields of interest from the raw items for boxplotting.
    * TODO: May throw an exception (double conversion step)
    * TODO: It would be more meaningful to precaculate boxplot quantiles here.
    *       However Highcharts expects boxplot quantiles as arguments, while plotly calculates them itself.
    *
    * @param title Plot title
    * @param items Raw items.
    * @param fieldNames Fields of interest.
    * @return BoxPlotSpec for use in view.
    */
  def box(
   title : String,
   items : Traversable[JsObject],
   fieldNames : Seq[String]
  ) : BoxPlotSpec =
    {
      val elements: Seq[(String, Seq[Double])] = fieldNames.map{ field =>
        val entries: Seq[Double] = items.map{ (item: JsObject) =>
          val entry: JsValue = (item \ field).get
          entry.toString.toDouble
        }.toSeq
        (field, entries)
      }
      BoxPlotSpec(title, elements)
    }
}