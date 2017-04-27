package models

import _root_.util.BasicStats.Quantiles
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID

abstract class Widget {
  val title: String
  val displayOptions: DisplayOptions
  val _id: BSONObjectID = BSONObjectID.generate()
}

case class CategoricalCountWidget(
  title: String,
  fieldName: String,
  fieldLabel: String,
  showLabels: Boolean,
  showLegend: Boolean,
  data: Seq[(String, Seq[Count[String]])],
  displayOptions: MultiChartDisplayOptions = MultiChartDisplayOptions()
) extends Widget

case class NumericalCountWidget[T](
  title: String,
  fieldLabel: String,
  data: Seq[(String, Seq[(T, Int)])],
  displayOptions: MultiChartDisplayOptions = MultiChartDisplayOptions()
) extends Widget

case class ScatterWidget(
  title: String,
  xAxisCaption: String,
  yAxisCaption: String,
  data: Seq[(String, String, Traversable[Seq[Any]])],
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends Widget

case class BoxWidget[T <% Ordered[T]](
  title: String,
  yAxisCaption: String,
  data: Quantiles[T],
  min: Option[T] = None,
  max: Option[T] = None,
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends Widget {
  def ordering = implicitly[Ordering[T]]
}

case class HeatmapWidget(
  title: String,
  xCategories: Seq[String],
  yCategories: Seq[String],
  data: Seq[Seq[Option[Double]]],
  min: Option[Double] = None,
  max: Option[Double] = None,
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends Widget

case class HtmlWidget(
  title: String,
  content: String,
  displayOptions: DisplayOptions = BasicDisplayOptions()
) extends Widget

case class Count[T](
  value: T,
  count: Int,
  key: Option[String]
)

// TODO: move elsewhere
object CategoricalCountWidget {

  def groupDataByValue(chartSpec: CategoricalCountWidget): Seq[(String, Seq[Int])] =
    chartSpec.data match {
      case Nil => Nil
      case series =>
        val firstSeriesValueLabels = series.head._2.map(_.value)

        val otherValueLabels = series.tail.map(_._2.map(_.value)).flatten.toSet.toSeq
        val firstSeriesValueLabelsSet = firstSeriesValueLabels.toSet
        val extraValueLabels = otherValueLabels.filterNot(firstSeriesValueLabelsSet)

        (firstSeriesValueLabels ++ extraValueLabels).map { value =>
          val counts = chartSpec.data.map { series =>
            series._2.find(_.value.equals(value)).map(_.count).getOrElse(0)
          }
          (value, counts)
        }
    }
}