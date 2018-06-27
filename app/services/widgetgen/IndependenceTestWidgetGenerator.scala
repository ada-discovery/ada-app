package services.widgetgen

import models._
import services.stats.calc.{MultiChiSquareTestCalcTypePack, MultiOneWayAnovaTestCalcTypePack}
import util.FieldUtil.InfixFieldOps

object ChiSquareTestWidgetGenerator extends CalculatorWidgetGenerator[IndependenceTestWidgetSpec, IndependenceTestWidget, MultiChiSquareTestCalcTypePack[Any, Any]]
  with NoOptionsCalculatorWidgetGenerator[IndependenceTestWidgetSpec] {

  override protected val seqExecutor = multiChiSquareTestExec[Any, Any]

  override protected val supportArray = false

  override protected def filterFields(fields: Seq[Field]) =
    if (fields.nonEmpty)
      Seq(fields.head) ++ fields.tail.filter(!_.isNumeric)
    else
      Nil

  override protected def extraStreamCriteria(
    spec: IndependenceTestWidgetSpec,
    fields: Seq[Field]
  ) =
    if (spec.keepUndefined)
      Nil
    else
      withNotNull(Seq(fields.head))

  override def apply(
    spec: IndependenceTestWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (results:  MultiChiSquareTestCalcTypePack[Any, Any]#OUT) => {
      val targetField = fieldNameMap.get(spec.fieldName).get
      val inputFields = spec.inputFieldNames.flatMap(fieldNameMap.get)
      val chartTitle = title(spec).getOrElse("Chi-square Test for " + targetField.labelOrElseName)

      val fieldResults = inputFields.zip(results)
        .flatMap { case (field, result) => result.map((field.labelOrElseName, _)) }
        .sortWith { case ((field1, result1), (field2, result2)) =>
          val pValue1 = result1.pValue
          val pValue2 = result2.pValue
          val stat1 = result1.statistics
          val stat2 = result2.statistics

          (pValue1 < pValue2) || (pValue1 == pValue2 && stat1 > stat2)
        }

      if (fieldResults.nonEmpty) {
        val widget = IndependenceTestWidget(chartTitle, fieldResults, spec.displayOptions)
        Some(widget)
      } else
        None
    }
}

object OneWayAnovaTestWidgetGenerator extends CalculatorWidgetGenerator[IndependenceTestWidgetSpec, IndependenceTestWidget, MultiOneWayAnovaTestCalcTypePack[Any]]
  with NoOptionsCalculatorWidgetGenerator[IndependenceTestWidgetSpec] {

  override protected val seqExecutor = multiOneWayAnovaTestExec[Any]

  override protected val supportArray = false

  override protected def filterFields(fields: Seq[Field]) =
    if (fields.nonEmpty)
      Seq(fields.head) ++ fields.tail.filter(_.isNumeric)
    else
      Nil

  override protected def extraStreamCriteria(
    spec: IndependenceTestWidgetSpec,
    fields: Seq[Field]
  ) =
    if (spec.keepUndefined)
      Nil
    else
      withNotNull(Seq(fields.head))

  override def apply(
    spec: IndependenceTestWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (results:  MultiOneWayAnovaTestCalcTypePack[Any]#OUT) => {
      val targetField = fieldNameMap.get(spec.fieldName).get
      val inputFields = spec.inputFieldNames.flatMap(fieldNameMap.get)
      val chartTitle = title(spec).getOrElse("ANOVA Test for " + targetField.labelOrElseName)

      val fieldResults = inputFields.zip(results)
        .flatMap { case (field, result) => result.map((field.labelOrElseName, _)) }
        .sortWith { case ((field1, result1), (field2, result2)) =>
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