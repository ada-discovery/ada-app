package services.widgetgen

import models._
import services.stats.{CalculatorTypePack, NoOptionsCalculatorTypePack}
import services.stats.calc._
import util.FieldUtil.InfixFieldOps

trait AbstractOneWayAnovaTestWidgetGenerator[C <: NoOptionsCalculatorTypePack] extends CalculatorWidgetGenerator[IndependenceTestWidgetSpec, IndependenceTestWidget, C]
  with NoOptionsCalculatorWidgetGenerator[IndependenceTestWidgetSpec] {

  override protected def filterFields(fields: Seq[Field]) =
    if (fields.nonEmpty)
      Seq(fields.head) ++ fields.tail.filter(_.isNumeric)
    else
      Nil

  protected def applyAux(
    spec: IndependenceTestWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (results: Seq[Option[OneWayAnovaResult]]) => {
      val targetField = fieldNameMap.get(spec.fieldName).get
      val inputFields = spec.inputFieldNames.flatMap(fieldNameMap.get)
      val chartTitle = title(spec).getOrElse("ANOVA Test for " + targetField.labelOrElseName)

      val fieldResults = inputFields.zip(results)
        .flatMap { case (field, result) => result.map((field.labelOrElseName, _)) }
        .sortWith { case ((_, result1), (_, result2)) =>
          val pValue1 = result1.pValue
          val pValue2 = result2.pValue
          val stat1 = result1.FValue
          val stat2 = result2.FValue

          (pValue1 < pValue2) || (pValue1 == pValue2 && stat1 > stat2)
        }

      if (fieldResults.nonEmpty) {
        val widget = IndependenceTestWidget(chartTitle, fieldResults, spec.displayOptions)
        Some(widget)
      } else
        None
    }
}

object OneWayAnovaTestWidgetGenerator extends AbstractOneWayAnovaTestWidgetGenerator[MultiOneWayAnovaTestCalcTypePack[Option[Any]]] {

  override protected val seqExecutor = multiOneWayAnovaTestExec[Option[Any]]

  override protected val supportArray = false

  override def apply(
    spec: IndependenceTestWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) = applyAux(spec)(fieldNameMap)
}

object NullExcludedOneWayAnovaTestWidgetGenerator extends AbstractOneWayAnovaTestWidgetGenerator[NullExcludedMultiOneWayAnovaTestCalcTypePack[Any]] {

  override protected val seqExecutor = nullExcludedMultiOneWayAnovaTestExec[Any]

  override protected val supportArray = false

  override protected def extraStreamCriteria(
    spec: IndependenceTestWidgetSpec,
    fields: Seq[Field]
  ) = withNotNull(Seq(fields.head))

  override def apply(
    spec: IndependenceTestWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) = applyAux(spec)(fieldNameMap)
}