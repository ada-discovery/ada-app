package services.widgetgen

import models._

object CorrelationWidgetGenerator extends WidgetGenerator[CorrelationWidgetSpec, HeatmapWidget, Seq[Seq[Option[Double]]]] {

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: CorrelationWidgetSpec
  ) =
    (correlations: Seq[Seq[Option[Double]]]) =>
      if (correlations.nonEmpty) {
        val fields = spec.fieldNames.flatMap(fieldNameMap.get)
        val fieldLabels = fields.map(_.labelOrElseName)

        val widget = HeatmapWidget(
          title(spec).getOrElse("Correlations"), fieldLabels, fieldLabels, correlations, Some(-1), Some(1), spec.displayOptions
        )
        Some(widget)
      } else
        None
}