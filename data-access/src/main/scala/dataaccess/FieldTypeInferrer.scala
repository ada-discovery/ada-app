package dataaccess

import dataaccess.FieldTypeInferrer.INFER_FIELD_TYPE
import play.api.libs.json.JsReadable

import collection.mutable.{Map => MMap}

trait FieldTypeInferrer {
  def apply(values: Traversable[String]): FieldType[_]
}

object FieldTypeInferrer {
  def apply(
    nullAliases: Set[String],
    dateFormats: Traversable[String],
    displayDateFormat: String,
    enumValuesMaxCount: Int
  ): FieldTypeInferrer =
    new FieldTypeInferrerImpl(nullAliases, dateFormats, displayDateFormat, enumValuesMaxCount)

  type INFER_FIELD_TYPE = Traversable[String] => Option[FieldType[_]]
}

private case class EnumFieldTypeInferrer(
    nullAliases: Set[String],
    enumValuesMaxCount: Int
  ) {

  def apply: INFER_FIELD_TYPE = { values =>
    val valuesWoNull = values.filterNot(value => value == null || nullAliases.contains(value.trim.toLowerCase))
    val countMap = MMap[String, Int]()
    valuesWoNull.foreach{value =>
      val count = countMap.getOrElse(value.trim, 0)
      countMap.update(value.trim, count + 1)
    }
//    val sum = countMap.values.sum
//    val freqsWoNa = countMap.values.map(count => count.toDouble / sum)
//    freqsWoNa.size =< enumValuesMaxCount && (freqsWoNa.sum / freqsWoNa.size) > enumFrequencyThreshold

    val distinctValues = countMap.keys.toSeq.sorted
    if (distinctValues.size <= enumValuesMaxCount) {
      val enumMap = distinctValues.zipWithIndex.toMap.map(_.swap)
      Some(EnumFieldType(nullAliases, enumValuesMaxCount, enumMap))
    } else
      None
  }
}

private case class EnumArrayFieldTypeInferrer(
    nullAliases: Set[String],
    enumValuesMaxCount: Int,
    delimiter: String
  ) {

  def apply: INFER_FIELD_TYPE = { values =>
    val arrayValuesWoNull = values.filterNot(value => value == null || nullAliases.contains(value.trim.toLowerCase))
    val valuesWoNull = arrayValuesWoNull.map(_.split(delimiter)).flatten

    // use scalar inferrer for all values
    val scalarEnumInferrer = EnumFieldTypeInferrer(nullAliases, enumValuesMaxCount).apply
    scalarEnumInferrer(valuesWoNull).map( enumType =>
      ArrayFieldType(enumType, delimiter)
    )
  }
}

private class FieldTypeInferrerImpl(
    nullAliases: Set[String],
    dateFormats: Traversable[String],
    displayDateFormat: String,
    enumValuesMaxCount: Int
  ) extends FieldTypeInferrer {

  private val arrayDelimiter = ","

  private val staticFieldTypes = FieldTypeFactory(nullAliases, dateFormats, displayDateFormat, enumValuesMaxCount).allStaticTypes
  private val defaultType = staticFieldTypes.find(_.spec.fieldType == FieldTypeId.String).get
  private val dynamicFieldInferrers: Seq[((FieldTypeId.Value, Boolean), INFER_FIELD_TYPE)] = Seq(
    (
      (FieldTypeId.Enum, false),
      EnumFieldTypeInferrer(nullAliases, enumValuesMaxCount).apply
    ),
    (
      (FieldTypeId.Enum, true),
      EnumArrayFieldTypeInferrer(nullAliases, enumValuesMaxCount, arrayDelimiter).apply
    )
  )

  private val prioritizedFieldTypes = Seq(
    (FieldTypeId.Null, false),
    (FieldTypeId.Null, true),
    (FieldTypeId.Boolean, false),
    (FieldTypeId.Integer, false),
    (FieldTypeId.Double, false),
    (FieldTypeId.Date, false),
    (FieldTypeId.Json, false),
    (FieldTypeId.Boolean, true),
    (FieldTypeId.Integer, true),
    (FieldTypeId.Double, true),
    (FieldTypeId.Date, true),
    (FieldTypeId.Json, true),
    (FieldTypeId.Enum, false),
    (FieldTypeId.Enum, true),
    (FieldTypeId.String, false),
    (FieldTypeId.String, true)
  )

  private val fieldTypeInferrers: Traversable[((FieldTypeId.Value, Boolean), INFER_FIELD_TYPE)] =
    staticFieldTypes.map(fieldType =>
      ((fieldType.spec.fieldType, fieldType.spec.isArray), isOfStaticType(fieldType))
    ) ++ dynamicFieldInferrers

  private val fieldTypeInferrerMap = fieldTypeInferrers.toMap

  private def isOfStaticType(fieldType: FieldType[_]): INFER_FIELD_TYPE = { texts =>
    val passed = texts.forall( text =>
      try {
        fieldType.displayStringToValue(text)
        true
      } catch {
        case e: AdaConversionException => false
      }
    )

    if (passed)
      Some(fieldType)
    else
      None
  }

  override def apply(values: Traversable[String]): FieldType[_] = {
    val fieldType = prioritizedFieldTypes.view.map( fieldTypeSpec =>
      fieldTypeInferrerMap.get(fieldTypeSpec).map(_(values)).flatten
    ).find(_.isDefined)

    fieldType match {
      case Some(fieldType) => fieldType.get
      // this should never happen, but who knows :)
      case None => defaultType
    }
  }
}