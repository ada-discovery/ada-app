package util

import play.api.libs.json._
import collection.mutable.{Map => MMap}
import _root_.util.JsonUtil._

abstract class ChartSpec(title: String)
case class PieChartSpec(title: String, showLabels: Boolean, showLegend: Boolean, data: Seq[(String, Int)]) extends ChartSpec(title)
case class ColumnChartSpec(title: String, data: Seq[(String, Int)]) extends ChartSpec(title)
case class ScatterChartSpec(title: String, data: Seq[Seq[Double]]) extends ChartSpec(title)
case class BoxPlotSpec(title: String, data: Seq[(String, Seq[Double])]) extends ChartSpec(title)

case class FieldChartSpec(fieldName : String, chartSpec : ChartSpec)

object ChartSpec {

  /**
    * Given the raw items and field name, all items per value are counted.
    * Unique values will be used as labels for the pie chart.
    * Calculated fraction of a field is used as pie chart slice sizes.
    *
    * @param items Raw items.
    * @param fieldName Fields of iterest.
    * @return PieChartSpec object for use in view.
    */
  def pieJson(
    items: Traversable[JsObject],
    fieldName: String,
    title: String,
    showLabels: Boolean,
    showLegend: Boolean
  ) : PieChartSpec = {
    val values = items.map{item =>
      val rawWalue = (item \ fieldName).get
      if (rawWalue == JsNull)
        "null"
      else
        rawWalue.as[String]
    }
    pie(values, title, showLabels, showLegend)
  }

  /**
    * Given the values the counts/frequencies are calculated.
    * Unique values will be used as labels for the pie chart.
    * Calculated fraction of a field is used as pie chart slice sizes.
    *
    * @param values Raw values.
    * @return PieChartSpec object for use in view.
    */
  def pie(
    values: Traversable[_],
    title: String,
    showLabels: Boolean,
    showLegend: Boolean
  ) : PieChartSpec = {
    val countMap = MMap[String, Int]()
    values.foreach{ value =>
      val count = countMap.getOrElse(value.toString, 0)
      countMap.update(value.toString, count + 1)
    }
    PieChartSpec(title, showLabels, showLegend, countMap.toSeq.sortBy(_._2))
  }

  /**
    * Given raw items and field names, column chart properties are generated.
    * Non-defined optional values are auto-calculated.
    *
    * @param items Raw items.
    * @param fieldName Fields of interest.
    * @param columnCount Number of columns
    * @param explMin Optional max value for scaling of the columns.
    * @param explMax Optional min value for scaling of the columns.
    * @return ColumnChartSpec for us in view.
    */
  def column(
    items: Traversable[JsObject],
    fieldName: String,
    title: String,
    columnCount: Int,
    explMin: Option[Double] = None,
    explMax: Option[Double] = None
  ) : ColumnChartSpec = {
    val values = project(items.toList, fieldName).map(toDouble).flatten
    val data = if (values.nonEmpty) {
      val min = if (explMin.isDefined) explMin.get else values.min
      val max = if (explMax.isDefined) explMax.get else values.max
      val stepSize = (max - min) / columnCount

      for (step <- 0 until columnCount) yield {
        val left = min + step * stepSize
        val right = left + stepSize
        val count = if (step == columnCount - 1) {
          values.filter(value => value >= left && value <= right).size
        } else {
          values.filter(value => value >= left && value < right).size
        }

        (left.toString, count)
      }
    } else
      Seq[(String, Int)]()
    ColumnChartSpec(title, data)
  }

  /**
    * Extracts the fields of interest from the raw item lists for plotting.
    *
    * @param items Raw items.
    * @param fieldName Fields of interest.
    * @return ScatterChartSpec for use in view.
    */
  def scatter(
    items: Traversable[(JsObject, JsObject)],
    fieldName: String,
    title: String
  ) : ScatterChartSpec =
    ScatterChartSpec(title,
      items.map { case (item1, item2) =>
        Seq(
          toDouble(item1 \ fieldName).get,
          toDouble(item2 \ fieldName).get
        )
      }.toSeq
    )


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