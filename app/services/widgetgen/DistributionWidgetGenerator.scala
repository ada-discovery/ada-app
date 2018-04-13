package services.widgetgen

import models._
import util.{fieldLabel, shorten}

object DistributionWidgetGenerator extends WidgetGenerator[DistributionWidgetSpec, Widget, Traversable[(String, Traversable[Count[Any]])]] {

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: DistributionWidgetSpec
  ) =
    (countSeries:  Traversable[(String, Traversable[Count[Any]])]) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val groupField = spec.groupFieldName.flatMap(fieldNameMap.get)
      val nonZeroCountExists = countSeries.exists(_._2.exists(_.count > 0))
      if (nonZeroCountExists) {
        val widget = applyAux(field, groupField, spec)(countSeries)
        Some(widget)
      } else
        Option.empty[Widget]
    }

  private def applyAux(
    field: Field,
    groupField: Option[Field],
    spec: DistributionWidgetSpec
  ) = (countSeries:  Traversable[(String, Traversable[Count[Any]])]) => {
    val displayOptions = spec.displayOptions
    val chartTitle = title(spec).getOrElse(createTitle(field, groupField))

    field.fieldType match {
      case FieldTypeId.String | FieldTypeId.Enum | FieldTypeId.Boolean | FieldTypeId.Null | FieldTypeId.Json =>
        // enforce the same categories in all the series
        val labelGroupedCounts = countSeries.flatMap(_._2).groupBy(_.value)
        val nonZeroLabelSumCounts = labelGroupedCounts.map { case (label, counts) =>
          (label, counts.map(_.count).sum)
        }.filter(_._2 > 0)

        val sortedLabels: Seq[String] = nonZeroLabelSumCounts.toSeq.sortBy(_._2).map(_._1.toString)

        val topSortedLabels  = spec.numericBinCount match {
          case Some(maxCategoricalBinCount) => sortedLabels.takeRight(maxCategoricalBinCount)
          case None => sortedLabels
        }

        val countSeriesSorted = countSeries.map { case (seriesName, counts) =>

          val labelCountMap = counts.map { count =>
            val label = count.value.toString
            (label, Count(label, count.count, count.key))
          }.toMap

          val newCounts = topSortedLabels.map ( label =>
            labelCountMap.get(label).getOrElse(Count(label, 0, None))
          )
          (seriesName, newCounts)
        }
        val nonZeroCountSeriesSorted = countSeriesSorted.filter(_._2.exists(_.count > 0)).toSeq

        val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Pie)))

        // create a categorica widget
        CategoricalCountWidget(
          chartTitle,
          field.name,
          field.labelOrElseName,
          false,
          true,
          spec.relativeValues,
          false,
          nonZeroCountSeriesSorted,
          initializedDisplayOptions
        )

      case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
        val nonZeroNumCountSeries = countSeries.filter(_._2.nonEmpty).toSeq
        val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))

        // create a numeric widget
        NumericalCountWidget(
          chartTitle,
          field.labelOrElseName,
          spec.relativeValues,
          false,
          nonZeroNumCountSeries,
          initializedDisplayOptions
        )
    }
  }

  private def createTitle(
    field: Field,
    groupField: Option[Field]
  ): String = {
    val label = field.label.getOrElse(fieldLabel(field.name))

    groupField.map { groupField =>
      val groupShortLabel = shorten(groupField.label.getOrElse(fieldLabel(groupField.name)), 25)
      shorten(label, 25) + " by " + groupShortLabel
    }.getOrElse(
      label
    )
  }
}