package services.widgetgen

import models.{BoxWidget, BoxWidgetSpec, Field}
import services.stats.calc.Quartiles

object BoxWidgetGenerator extends WidgetGenerator[BoxWidgetSpec, BoxWidget[Any], Quartiles[Any]] {

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: BoxWidgetSpec
  ) =
    (quartiles:  Quartiles[Any]) => {
      implicit val ordering = quartiles.ordering
      val field = fieldNameMap.get(spec.fieldName).get
      val chartTitle = title(spec).getOrElse(field.labelOrElseName)
      val widget = BoxWidget[Any](chartTitle, field.labelOrElseName, quartiles, None, None, spec.displayOptions)
      Some(widget)
    }
}