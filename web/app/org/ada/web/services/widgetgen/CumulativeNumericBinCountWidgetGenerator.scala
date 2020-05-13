package org.ada.web.services.widgetgen

import org.ada.server.calc.impl.{CumulativeNumericBinCountsCalcTypePack, GroupCumulativeNumericBinCountsCalcTypePack, GroupNumericDistributionCountsCalcTypePack, NumericDistributionFlowOptions, NumericDistributionOptions}
import org.ada.server.field.{FieldType, FieldTypeHelper}
import org.ada.server.models.{CumulativeCountWidgetSpec, Field}
import org.ada.web.models.{Count, NumericalCountWidget}

private class CumulativeNumericBinCountWidgetGenerator(
  flowMin: Double,
  flowMax: Double
) extends CalculatorWidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], CumulativeNumericBinCountsCalcTypePack]
  with CumulativeCountWidgetGeneratorHelper {

  override protected val seqExecutor = cumulativeNumericBinCountsSeqExec

  override protected def specToOptions = (spec: CumulativeCountWidgetSpec) =>
    NumericDistributionOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount))

  override protected def specToFlowOptions = (spec: CumulativeCountWidgetSpec) =>
    NumericDistributionFlowOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount), flowMin, flowMax)

  override protected def specToSinkOptions = specToFlowOptions

  override protected val supportArray = true

  override protected def extraStreamCriteria(
    spec: CumulativeCountWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields)

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (valueCounts: CumulativeNumericBinCountsCalcTypePack#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get

      val counts = createNumericCounts(valueCounts, convertNumeric(field.fieldType))
      createNumericWidget(spec, field, None)(Seq(("All", counts)))
    }
}

object CumulativeNumericBinCountWidgetGenerator {

  type GEN = CalculatorWidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], CumulativeNumericBinCountsCalcTypePack]

  def apply(
    flowMin: Double,
    flowMax: Double
  ): GEN = new CumulativeNumericBinCountWidgetGenerator(flowMin, flowMax)

  def apply(
    flowMinMax: (Double, Double)
  ): GEN = apply(flowMinMax._1, flowMinMax._2)
}

private class GroupCumulativeNumericBinCountWidgetGenerator(
  flowMin: Double,
  flowMax: Double
) extends CalculatorWidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], GroupCumulativeNumericBinCountsCalcTypePack[Any]]
  with CumulativeCountWidgetGeneratorHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override protected val seqExecutor = groupCumulativeNumericBinCountsSeqExec[Any]

  override protected def specToOptions = (spec: CumulativeCountWidgetSpec) =>
    NumericDistributionOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount))

  override protected def specToFlowOptions = (spec: CumulativeCountWidgetSpec) =>
    NumericDistributionFlowOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount), flowMin, flowMax)

  override protected def specToSinkOptions = specToFlowOptions

  override protected val supportArray = true

  override protected def extraStreamCriteria(
    spec: CumulativeCountWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(fields.tail)

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (valueCounts: GroupCumulativeNumericBinCountsCalcTypePack[Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val groupField = fieldNameMap.get(spec.groupFieldName.get).get
      val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]

      val counts = createGroupNumericCounts(valueCounts, groupFieldType, field)
      createNumericWidget(spec, field, Some(groupField))(counts)
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

object GroupCumulativeNumericBinCountWidgetGenerator {

  type GEN = CalculatorWidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any], GroupCumulativeNumericBinCountsCalcTypePack[Any]]

  def apply(
    flowMin: Double,
    flowMax: Double
  ): GEN = new GroupCumulativeNumericBinCountWidgetGenerator(flowMin, flowMax)

  def apply(
    flowMinMax: (Double, Double)
  ): GEN = apply(flowMinMax._1, flowMinMax._2)
}