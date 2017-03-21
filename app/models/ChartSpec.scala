package models

import _root_.util.BasicStats.Quantiles
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID

abstract class ChartSpec {
  val title: String
  val displayOptions: DisplayOptions
  val _id: BSONObjectID = BSONObjectID.generate()
}

case class CategoricalChartSpec(
  title: String,
  showLabels: Boolean,
  showLegend: Boolean,
  data: Seq[(String, Seq[Count[String]])],
  displayOptions: ChartDisplayOptions = ChartDisplayOptions()
) extends ChartSpec

case class NumericalChartSpec[T](
  title: String,
  data: Seq[(String, Seq[(T, Int)])],
  displayOptions: ChartDisplayOptions = ChartDisplayOptions()
) extends ChartSpec

case class ColumnChartSpec(
  title: String,
  data: Seq[(String, Int)],
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends ChartSpec

case class ScatterChartSpec(
  title: String,
  xAxisCaption: String,
  yAxisCaption: String,
  data: Seq[(String, String, Seq[Seq[Any]])],
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends ChartSpec

case class BoxChartSpec[T <% Ordered[T]](
  title: String,
  yAxisCaption: String,
  data: Quantiles[T],
  min: Option[T] = None,
  max: Option[T] = None,
  displayOptions: DisplayOptions = BasicDisplayOptions()
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
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends ChartSpec

case class Count[T](
  value: T,
  count: Int,
  key: Option[String]
)

case class FieldChartSpec(fieldName: String, chartSpec : ChartSpec)