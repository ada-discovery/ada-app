package models

import _root_.util.BasicStats.Quantiles
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID

import scala.collection.mutable
import scala.collection.mutable.{Map => MMap}
import scala.math.BigDecimal.RoundingMode
import models.ChartType

abstract class ChartSpec {
  val title: String
  val height: Option[Int]
  val gridWidth: Option[Int]
  val _id: BSONObjectID = BSONObjectID.generate()
}

case class CategoricalChartSpec(
  title: String,
  showLabels: Boolean,
  showLegend: Boolean,
  data: Seq[DataPoint],
  chartType: ChartType.Value,
  height: Option[Int] = None,
  gridWidth: Option[Int] = None
) extends ChartSpec

case class NumericalChartSpec(
  title: String,
  data: Seq[(String, Int)],
  chartType: ChartType.Value,
  height: Option[Int] = None,
  gridWidth: Option[Int] = None
) extends ChartSpec

case class ColumnChartSpec(
  title: String,
  data: Seq[(String, Int)],
  height: Option[Int] = None,
  gridWidth: Option[Int] = None
) extends ChartSpec

case class ScatterChartSpec(
  title: String,
  xAxisCaption: String,
  yAxisCaption: String,
  data: Seq[(String, String, Seq[Seq[Any]])],
  height: Option[Int] = None,
  gridWidth: Option[Int] = None
) extends ChartSpec

case class BoxChartSpec[T](
  title: String,
  yAxisCaption: String,
  data: Quantiles[T],
  height: Option[Int] = None,
  gridWidth: Option[Int] = None
) extends ChartSpec

case class HeatmapChartSpec(
  title: String,
  xCategories: Seq[String],
  yCategories: Seq[String],
  data: Seq[Seq[Option[Double]]],
  min: Option[Double] = None,
  max: Option[Double] = None,
  height: Option[Int] = None,
  gridWidth: Option[Int] = None
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
    renderer: Option[Option[T] => String],
    title: String,
    showLabels: Boolean,
    showLegend: Boolean,
    chartType: Option[ChartType.Value] = None,
    outputGridWidth: Option[Int] = None
  ): CategoricalChartSpec = {
    val countMap = MMap[Option[T], Int]()
    values.foreach { value =>
      val count = countMap.getOrElse(value, 0)
      countMap.update(value, count + 1)
    }
    categoricalPlain(countMap.toSeq, renderer, title, showLabels, showLegend, chartType, outputGridWidth)
  }

  def categoricalPlain[T](
    counts: Seq[(Option[T], Int)],
    renderer: Option[Option[T] => String],
    title: String,
    showLabels: Boolean,
    showLegend: Boolean,
    chartType: Option[ChartType.Value] = None,
    outputGridWidth: Option[Int] = None
  ): CategoricalChartSpec = {
    val data = counts.sortBy(_._2).map {
      case (key, count) => {
        val stringKey = key.map(_.toString)
        val keyOrEmpty = stringKey.getOrElse("")
        DataPoint(
          stringKey,
          count,
          renderer.map(_.apply(key)).getOrElse(keyOrEmpty))
      }
    }
    CategoricalChartSpec(title, showLabels, showLegend, data, chartType.getOrElse(ChartType.Pie), None, outputGridWidth)
  }

  def numerical(
    counts: Traversable[(BigDecimal, Int)],
    title: String,
    chartType: Option[ChartType.Value] = None,
    xAxisLabel: Option[BigDecimal => String] = None,
    outputGridWidth: Option[Int] = None
  ): NumericalChartSpec = {
    val stringCounts = counts.toSeq.sortBy(_._1).map { case (xValue, count) =>
      val xLabel = xAxisLabel.map(_.apply(xValue)).getOrElse(xValue.toString)
      (xLabel, count)
    }
    NumericalChartSpec(title, stringCounts, chartType.getOrElse(ChartType.Column), None, outputGridWidth)
  }
}