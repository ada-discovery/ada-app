package org.ada.web.services.widgetgen

import org.ada.server.AdaException
import org.ada.server.calc.impl.XOrderedSeqCalcTypePack
import org.ada.server.models._
import org.ada.web.models.LineWidget
import org.ada.web.util.shorten

private class LineWidgetGenerator
  extends CalculatorWidgetGenerator[XLineWidgetSpec, LineWidget[Any, Any], XOrderedSeqCalcTypePack[Any]]
    with NoOptionsCalculatorWidgetGenerator[XLineWidgetSpec] {

  override protected val seqExecutor = xOrderedSeqAnyExec

  override protected val supportArray = false

  override def apply(
    spec: XLineWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (xSeq: XOrderedSeqCalcTypePack[Any]#OUT) =>
      if (xSeq.nonEmpty) {
        val xField = fieldNameMap.get(spec.xFieldName).getOrElse(
          throw new AdaException(s"X field '${spec.xFieldName}' not found.")
        )

        val data = spec.yFieldNames.zipWithIndex.map { case (yFieldName, index) =>
          val seq = xSeq.flatMap { case (x, seq) => seq(index).map((x, _))}.toSeq

          val yField = fieldNameMap.get(yFieldName).getOrElse(
            throw new AdaException(s"Y field '${yFieldName}' not found.")
          )

          (yField.labelOrElseName, seq)
        }

        val yFieldLabels = data.map(_._1).mkString(", ")

        val widget = LineWidget[Any, Any](
          title(spec).getOrElse(shorten(s"${xField.labelOrElseName} vs. $yFieldLabels", 60)),
          spec.xFieldName,
          xAxisCaption = xField.labelOrElseName,
          yAxisCaption = "Value",
          data = data,
          displayOptions = spec.displayOptions
        )
        Some(widget)
      } else
        None
}

