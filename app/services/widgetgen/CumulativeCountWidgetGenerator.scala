package services.widgetgen

import dataaccess.{FieldType, FieldTypeHelper}
import models._
import services.stats.calc._
import util.{fieldLabel, shorten}

object CumulativeCountWidgetGenerator extends WidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], CumulativeOrderedCountsCalcTypePack[Any]#OUT] with CumulativeCountWidgetGeneratorHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: CumulativeCountWidgetSpec
  ) =
    (valueCounts:  CumulativeOrderedCountsCalcTypePack[Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val counts = field.fieldType match {
        // numeric
        case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
          valueCounts.map { case (value, count) => Count(value, count) }

        // string
        case _ =>
          val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
          createStringCountsDefined(valueCounts, fieldType)
      }

      createWidget(spec, field, None)(Seq(("All", counts)))
    }
}

object GroupCumulativeCountWidgetGenerator extends WidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], GroupCumulativeOrderedCountsCalcTypePack[Any, Any]#OUT] with CumulativeCountWidgetGeneratorHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: CumulativeCountWidgetSpec
  ) =
    (groupCounts:  GroupCumulativeOrderedCountsCalcTypePack[Any, Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val groupField = fieldNameMap.get(spec.groupFieldName.get).get

      val stringGroupCounts = field.fieldType match {
        // group numeric
        case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
          val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]

          toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, valueCounts) =>
            val counts = valueCounts.map { case (value, count) => Count(value, count)}
            (groupString, counts)
          }

        // group string
        case _ =>
          val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
          val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]

          toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, valueCounts) =>
            (groupString, createStringCountsDefined(valueCounts, fieldType))
          }
      }

      // create a widget
      createWidget(spec, field, Some(groupField))(stringGroupCounts)
    }
}

object CumulativeNumericBinCountWidgetGenerator extends WidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], CumulativeNumericBinCountsCalcTypePack#OUT] with CumulativeCountWidgetGeneratorHelper {

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: CumulativeCountWidgetSpec
  ) =
    (valueCounts:  CumulativeNumericBinCountsCalcTypePack#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get

      val counts = createNumericCounts(valueCounts, convertNumeric(field.fieldType))
      createWidget(spec, field, None)(Seq(("All", counts)))
    }
}

object GroupCumulativeNumericBinCountWidgetGenerator extends WidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], GroupCumulativeNumericBinCountsCalcTypePack[Any]#OUT] with CumulativeCountWidgetGeneratorHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: CumulativeCountWidgetSpec
  ) =
    (valueCounts:  GroupCumulativeNumericBinCountsCalcTypePack[Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val groupField = fieldNameMap.get(spec.groupFieldName.get).get
      val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]

      val counts = createGroupNumericCounts(valueCounts, groupFieldType, field)
      createWidget(spec, field, Some(groupField))(counts)
    }

  private def createGroupNumericCounts[G](
    groupCounts: GroupNumericDistributionCountsCalcTypePack[G]#OUT,
    groupFieldType: FieldType[G],
    field: Field
  ): Seq[(String, Traversable[Count[_]])] = {
    // value converter
    val convert = convertNumeric(field.fieldType)

    // handle group string names and convert values
    toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, counts) =>
      (groupString, createNumericCounts(counts, convert))
    }
  }
}

@Deprecated
object CumulativeCountWidgetGenerator2 extends WidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], Traversable[(String, Traversable[Count[Any]])]] with CumulativeCountWidgetGeneratorHelper {

  override def apply(
    fieldNameMap: Map[String, Field],
    spec: CumulativeCountWidgetSpec
  ) = {
    val field = fieldNameMap.get(spec.fieldName).get
    val groupField = spec.groupFieldName.flatMap(fieldNameMap.get)

    createWidget(spec, field, groupField)
  }
}

trait CumulativeCountWidgetGeneratorHelper {

  protected def createWidget(
    spec: CumulativeCountWidgetSpec,
    field: Field,
    groupField: Option[Field]
  ) =
    (countSeries:  Traversable[(String, Traversable[Count[Any]])]) => {
      val displayOptions = spec.displayOptions
      val title = displayOptions.title.getOrElse(createTitle(field, groupField))

      val nonZeroCountSeries = countSeries.filter(_._2.exists(_.count > 0))
      if (nonZeroCountSeries.nonEmpty) {
        val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))
        val widget = NumericalCountWidget(title, field.labelOrElseName, spec.relativeValues, true, nonZeroCountSeries.toSeq, initializedDisplayOptions)
        Some(widget)
      } else
        None
    }

  protected def toGroupStringValues[G, T](
    groupCounts: Traversable[(Option[G], Traversable[T])],
    groupFieldType: FieldType[G]
  ): Seq[(String, Traversable[T])] =
    groupCounts.toSeq.sortBy(_._1.isEmpty).map { case (group, values) =>
      val groupString = group match {
        case Some(group) => groupFieldType.valueToDisplayString(Some(group))
        case None => "Undefined"
      }
      (groupString, values)
    }

  protected def createStringCountsDefined[T](
    counts: Traversable[(T, Int)],
    fieldType: FieldType[T]
  ): Traversable[Count[String]] =
    counts.map { case (value, count) =>
      val stringKey = value.toString
      val label = fieldType.valueToDisplayString(Some(value))
      Count(label, count, Some(stringKey))
    }

  protected def createNumericCounts(
    counts: NumericDistributionCountsCalcTypePack#OUT,
    convert: Option[BigDecimal => Any] = None
  ): Seq[Count[_]] =
    counts.toSeq.sortBy(_._1).map { case (xValue, count) =>
      val convertedValue = convert.map(_.apply(xValue)).getOrElse(xValue.toDouble)
      Count(convertedValue, count, None)
    }

  protected def convertNumeric(fieldType: FieldTypeId.Value) =
    fieldType match {
      case FieldTypeId.Date =>
        val convert = {ms: BigDecimal => new java.util.Date(ms.setScale(0, BigDecimal.RoundingMode.CEILING).toLongExact)}
        Some(convert)
      case _ => None
    }

  protected def createTitle(
    field: Field,
    groupField: Option[Field]
  ): String =
    groupField match {
      case Some(groupField) =>
        shorten(fieldLabel(field), 25) + " by " + shorten(fieldLabel(groupField), 25)

      case None =>
        fieldLabel(field)
    }
}