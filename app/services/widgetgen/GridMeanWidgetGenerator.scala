package services.widgetgen

import models._
import services.stats.calc.{NumericDistributionFlowOptions, NumericDistributionOptions}
import services.stats.calc.SeqBinMeanCalc.SeqBinMeanCalcTypePack
import util.shorten

private class GridMeanWidgetGenerator(
    xFlowMin: Double,
    xFlowMax: Double,
    yFlowMin: Double,
    yFlowMax: Double
  ) extends CalculatorWidgetGenerator[GridMeanWidgetSpec, HeatmapWidget, SeqBinMeanCalcTypePack] {

  override protected val seqExecutor = seqBinMeanExec

  override protected def specToOptions = (spec: GridMeanWidgetSpec) =>
   Seq(
     NumericDistributionOptions(spec.xBinCount),
     NumericDistributionOptions(spec.yBinCount)
   )

  override protected def specToFlowOptions = (spec: GridMeanWidgetSpec) =>
    Seq(
      NumericDistributionFlowOptions(spec.xBinCount, xFlowMin, xFlowMax),
      NumericDistributionFlowOptions(spec.yBinCount, yFlowMin, yFlowMax)
    )

  override protected def specToSinkOptions = specToFlowOptions

  override protected val supportArray = false

  override def apply(
    spec: GridMeanWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (indecesMeans: SeqBinMeanCalcTypePack#OUT) =>
      if (indecesMeans.nonEmpty) {
        def label(fieldName: String) = shorten(fieldNameMap.get(fieldName).get.labelOrElseName, 20)

        val caption = title(spec).getOrElse(s"${label(spec.valueFieldName)} (Mean): ${label(spec.xFieldName)} vs. ${label(spec.yFieldName)}")
        val groupedIndecesMeans = indecesMeans.toSeq.grouped(spec.yBinCount).toList

        val means = groupedIndecesMeans.map {_.map { case (_, means) => means }}

        val yValues = groupedIndecesMeans.head.map(_._1(1).setScale(2, BigDecimal.RoundingMode.FLOOR).toString)
        val xValues = groupedIndecesMeans.map(_.head._1(0).setScale(2, BigDecimal.RoundingMode.FLOOR).toString)

        val definedMeans = indecesMeans.flatMap(_._2)

        val widget = HeatmapWidget(
          caption, xValues, yValues, Some(label(spec.xFieldName)), Some(label(spec.yFieldName)), means, Some(definedMeans.min), Some(definedMeans.max), false, spec.displayOptions
        )
        Some(widget)
      } else
        None
}

object GridMeanWidgetGenerator {

  type GEN = CalculatorWidgetGenerator[GridMeanWidgetSpec, HeatmapWidget, SeqBinMeanCalcTypePack]

  def apply(
    xFlowMin: Double,
    xFlowMax: Double,
    yFlowMin: Double,
    yFlowMax: Double
  ): GEN = new GridMeanWidgetGenerator(xFlowMin, xFlowMax, yFlowMin, yFlowMax)


  def apply(
    xFlowMinMax: (Double, Double),
    yFlowMinMax: (Double, Double)
  ): GEN = apply(xFlowMinMax._1, xFlowMinMax._2, yFlowMinMax._1, yFlowMinMax._2)
}