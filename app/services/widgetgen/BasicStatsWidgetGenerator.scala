package services.widgetgen

import models._
import services.stats.calc.{BasicStatsCalcTypePack, BasicStatsResult}

object BasicStatsWidgetGenerator extends WidgetGenerator[BasicStatsWidgetSpec, BasicStatsWidget, BasicStatsCalcTypePack#OUT] {

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: BasicStatsWidgetSpec
  ) =
    (results:  BasicStatsCalcTypePack#OUT) =>
      results.map { results =>
        val field = fieldNameMap.get(spec.fieldName).get
        val chartTitle = title(spec).getOrElse(field.labelOrElseName)
        BasicStatsWidget(chartTitle, field.labelOrElseName, results, spec.displayOptions)
      }
}