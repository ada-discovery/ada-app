package services.widgetgen

import models._
import util.{fieldLabel, shorten}

object CumulativeCountWidgetGenerator extends WidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], Traversable[(String, Traversable[Count[Any]])]] {

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: CumulativeCountWidgetSpec
  ) =
    (countSeries:  Traversable[(String, Traversable[Count[Any]])]) => {
      val displayOptions = spec.displayOptions
      val field = fieldNameMap.get(spec.fieldName).get
      val groupField = spec.groupFieldName.flatMap(fieldNameMap.get)

      val nonZeroCountSeries = countSeries.filter(_._2.exists(_.count > 0))
      if (nonZeroCountSeries.nonEmpty) {
        val chartTitle = title(spec).getOrElse(createTitle(field, groupField))

        val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))
        val widget = NumericalCountWidget(chartTitle, field.labelOrElseName, spec.relativeValues, true, nonZeroCountSeries.toSeq, initializedDisplayOptions)
        Some(widget)
      } else
        None
    }

  private def createTitle(
    field: Field,
    groupField: Option[Field]
  ): String = {
    val label = field.label.getOrElse(fieldLabel(field.name))

    groupField match {
      case Some(groupField) =>
        val groupShortLabel = shorten(groupField.label.getOrElse(fieldLabel(groupField.name)), 25)
        shorten(label, 25) + " by " + groupShortLabel
      case None => label
    }
  }
}