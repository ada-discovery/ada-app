package services.widgetgen

import models._
import services.stats.calc.{GroupTupleCalcTypePack, TupleCalcTypePack}
import util.shorten

private class ScatterWidgetGenerator[T1, T2] extends WidgetGenerator[ScatterWidgetSpec, ScatterWidget[T1, T2], TupleCalcTypePack[T1, T2]#OUT] {

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: ScatterWidgetSpec
  ) =
    (data: TupleCalcTypePack[T1, T2]#OUT) =>
      if (data.nonEmpty) {
        val xField = fieldNameMap.get(spec.xFieldName).get
        val yField = fieldNameMap.get(spec.yFieldName).get
        val shortXFieldLabel = shorten(xField.labelOrElseName, 20)
        val shortYFieldLabel = shorten(yField.labelOrElseName, 20)

        val finalData = Seq(("all", data))

        val widget = ScatterWidget(
          title(spec).getOrElse(s"$shortXFieldLabel vs. $shortYFieldLabel"),
          xField.labelOrElseName,
          yField.labelOrElseName,
          finalData,
          spec.displayOptions
        )
        Some(widget)
      } else
        None
}

private class GroupScatterWidgetGenerator[T1, T2] extends WidgetGenerator[ScatterWidgetSpec, ScatterWidget[T1, T2], GroupTupleCalcTypePack[String, T1, T2]#OUT] {

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: ScatterWidgetSpec
  ) =
    (data: GroupTupleCalcTypePack[String, T1, T2]#OUT) =>
      if (data.nonEmpty) {
        val xField = fieldNameMap.get(spec.xFieldName).get
        val yField = fieldNameMap.get(spec.yFieldName).get
        val shortXFieldLabel = shorten(xField.labelOrElseName, 20)
        val shortYFieldLabel = shorten(yField.labelOrElseName, 20)
        val finalData = data.map { case (groupName, values) => (groupName.getOrElse("Undefined"), values)}.toSeq.sortBy(_._1)

        val widget = ScatterWidget(
          title(spec).getOrElse(s"$shortXFieldLabel vs. $shortYFieldLabel"),
          xField.labelOrElseName,
          yField.labelOrElseName,
          finalData,
          spec.displayOptions
        )
        Some(widget)
      } else
        None
}

object ScatterWidgetGenerator {
  def apply[T1, T2]: WidgetGenerator[ScatterWidgetSpec, ScatterWidget[T1, T2], TupleCalcTypePack[T1, T2]#OUT] = new ScatterWidgetGenerator[T1, T2]
}

object GroupScatterWidgetGenerator {
  def apply[T1, T2]: WidgetGenerator[ScatterWidgetSpec, ScatterWidget[T1, T2], GroupTupleCalcTypePack[String, T1, T2]#OUT] = new GroupScatterWidgetGenerator[T1, T2]
}