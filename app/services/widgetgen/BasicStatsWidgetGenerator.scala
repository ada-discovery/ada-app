package services.widgetgen

import models._
import services.stats.calc.BasicStatsResult

object BasicStatsWidgetGenerator extends WidgetGenerator[BasicStatsWidgetSpec, BasicStatsWidget, BasicStatsResult] {

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: BasicStatsWidgetSpec
  ) =
    (results:  BasicStatsResult) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val chartTitle = title(spec).getOrElse(field.labelOrElseName)
      val widget = BasicStatsWidget(chartTitle, field.labelOrElseName, results, spec.displayOptions)
      Some(widget)
    }
}