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
  data: Seq[(String, Seq[Count])],
  chartType: ChartType.Value,
  height: Option[Int] = None,
  gridWidth: Option[Int] = None
) extends ChartSpec

case class NumericalChartSpec(
  title: String,
  data: Seq[(String, Seq[(String, Int)])],
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

case class BoxChartSpec[T <% Ordered[T]](
  title: String,
  yAxisCaption: String,
  data: Quantiles[T],
  min: Option[T] = None,
  max: Option[T] = None,
  height: Option[Int] = None,
  gridWidth: Option[Int] = None
) extends ChartSpec {
  def ordering = implicitly[Ordering[T]]
}

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

case class Count(
  key: Option[String],
  count: Int,
  label: String
)

case class FieldChartSpec(fieldName: String, chartSpec : ChartSpec)