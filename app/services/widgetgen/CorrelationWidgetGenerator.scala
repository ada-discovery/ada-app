package services.widgetgen

import models._
import services.stats.calc.PearsonCorrelationCalcTypePack

private class CorrelationWidgetGenerator(flowParallelism: Option[Int]) extends CalculatorWidgetGenerator[CorrelationWidgetSpec, HeatmapWidget, PearsonCorrelationCalcTypePack] {

  override protected val seqExecutor = pearsonCorrelationExec

  override protected def specToOptions = _ => ()

  override protected def specToFlowOptions = _ => flowParallelism

  override protected def specToSinkOptions = _ => flowParallelism

  override protected val supportArray = false

  override def apply(
    spec: CorrelationWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (correlations: PearsonCorrelationCalcTypePack#OUT) =>
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

object CorrelationWidgetGenerator {
  def apply(flowParallelism: Option[Int]): CalculatorWidgetGenerator[CorrelationWidgetSpec, HeatmapWidget, PearsonCorrelationCalcTypePack] = new CorrelationWidgetGenerator(flowParallelism)
}