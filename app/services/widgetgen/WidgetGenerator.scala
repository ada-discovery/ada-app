package services.widgetgen

import models.{Field, Widget, WidgetSpec}

trait WidgetGenerator[S <: WidgetSpec, W <: Widget, IN] {

  def apply(
    fieldNameMap: Map[String, Field],
    spec: S
  ): IN => Option[W]

  def apply(
    fields: Seq[Field],
    spec: S
  ): IN => Option[W] = apply(fields.map(field => field.name -> field).toMap, spec)

  protected def title(widgetSpec: WidgetSpec) = widgetSpec.displayOptions.title
}
