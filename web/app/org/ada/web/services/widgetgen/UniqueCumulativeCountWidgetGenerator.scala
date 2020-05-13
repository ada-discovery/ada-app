package org.ada.web.services.widgetgen

import org.ada.server.calc.impl.GroupUniqueDistributionCountsCalcTypePack
import org.ada.server.calc.impl.UniqueDistributionCountsCalc.UniqueDistributionCountsCalcTypePack
import org.ada.server.field.{FieldType, FieldTypeHelper}
import org.ada.server.models.{CumulativeCountWidgetSpec, Field}
import org.ada.web.models.{Count, NumericalCountWidget}

object UniqueCumulativeCountWidgetGenerator extends WidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any]] with CumulativeCountWidgetGeneratorHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override type IN = UniqueDistributionCountsCalcTypePack[Any]#OUT

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) = (counts: IN) => {
    val field = fieldNameMap.get(spec.fieldName).get
    val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
    val finalCounts = Seq(("All", createStringCounts(counts, fieldType)))
    createNumericWidget(spec, field, None)(toCumCounts(finalCounts))
  }
}

object GroupUniqueCumulativeCountWidgetGenerator extends WidgetGenerator[CumulativeCountWidgetSpec, NumericalCountWidget[Any]] with CumulativeCountWidgetGeneratorHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override type IN = GroupUniqueDistributionCountsCalcTypePack[Any, Any]#OUT

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) = (groupCounts: IN) => {
    val field = fieldNameMap.get(spec.fieldName).get
    val groupField = fieldNameMap.get(spec.groupFieldName.get).get
    val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
    val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]

    val finalCounts = createGroupStringCounts(groupCounts, groupFieldType, fieldType)
    createNumericWidget(spec, field, Some(groupField))(toCumCounts(finalCounts))
  }

  private def createGroupStringCounts[G, T](
    groupCounts: Traversable[(Option[G], Traversable[(Option[T], Int)])],
    groupFieldType: FieldType[G],
    fieldType: FieldType[T]
  ): Seq[(String, Traversable[Count[String]])] =
    toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, counts) =>
      (groupString, createStringCounts(counts, fieldType))
    }
}
