package services.widgetgen

import models._
import util.shorten

private class ScatterWidgetGenerator[T1, T2] extends WidgetGenerator[ScatterWidgetSpec, ScatterWidget[T1, T2], Seq[(String, Traversable[(T1, T2)])]] {

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: ScatterWidgetSpec
  ) =
    (data: Seq[(String, Traversable[(T1, T2)])]) =>
      if (data.nonEmpty) {
        val xField = fieldNameMap.get(spec.xFieldName).get
        val yField = fieldNameMap.get(spec.yFieldName).get
        val shortXFieldLabel = shorten(xField.labelOrElseName, 20)
        val shortYFieldLabel = shorten(yField.labelOrElseName, 20)

        val widget = ScatterWidget(
          title(spec).getOrElse(s"$shortXFieldLabel vs. $shortYFieldLabel"),
          xField.labelOrElseName,
          yField.labelOrElseName,
          data.sortBy(_._1),
          spec.displayOptions
        )
        Some(widget)
      } else
        None
}

object ScatterWidgetGenerator {
  def apply[T1, T2]: WidgetGenerator[ScatterWidgetSpec, ScatterWidget[T1, T2], Seq[(String, Traversable[(T1, T2)])]] = new ScatterWidgetGenerator[T1, T2]
}