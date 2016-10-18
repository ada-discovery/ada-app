package dataaccess

import collection.mutable.{Map => MMap}

trait FieldTypeInferrer {
  def apply(values: Traversable[String]): FieldType[_]
}

object FieldTypeInferrer {
  def apply(
    nullAliases: Set[String],
    dateFormats: Traversable[String],
    enumValuesMaxCount: Int
  ): FieldTypeInferrer =
    new FieldTypeInferrerImpl(nullAliases, dateFormats, enumValuesMaxCount)
}

private case class EnumFieldTypeInferrer(
    nullAliases: Set[String],
    enumValuesMaxCount: Int
  ) {

  def apply(values: Traversable[String]): Option[FieldType[_]] = {
    val valuesWoNull = values.filterNot(value => nullAliases.contains(value.toLowerCase))
    val countMap = MMap[String, Int]()
    valuesWoNull.foreach{value =>
      val count = countMap.getOrElse(value, 0)
      countMap.update(value, count + 1)
    }
//    val sum = countMap.values.sum
//    val freqsWoNa = countMap.values.map(count => count.toDouble / sum)
//    freqsWoNa.size =< enumValuesMaxCount && (freqsWoNa.sum / freqsWoNa.size) > enumFrequencyThreshold

    val distinctValues = countMap.keys
    if (distinctValues.size <= enumValuesMaxCount) {
      val enumMap = distinctValues.zipWithIndex.toMap.map(_.swap)
      Some(EnumFieldType(nullAliases, enumValuesMaxCount, enumMap))
    } else
      None
  }
}

private class FieldTypeInferrerImpl(
    nullAliases: Set[String],
    dateFormats: Traversable[String],
    enumValuesMaxCount: Int
  ) extends FieldTypeInferrer {

  private val staticFieldTypes = FieldTypeFactory(nullAliases, dateFormats, enumValuesMaxCount).allStaticTypes
  private val defaultType = staticFieldTypes.find(_.spec.fieldType == FieldTypeId.String).get
  private val nonDefaultTypes = staticFieldTypes.filter(_.spec.fieldType != FieldTypeId.String)

  private val dynamicFieldInferrers = Seq(EnumFieldTypeInferrer(nullAliases, enumValuesMaxCount))

  private val prioritizedFieldTypes = Seq(
    FieldTypeId.Null,
    FieldTypeId.Integer,
    FieldTypeId.Double,
    FieldTypeId.Boolean,
    FieldTypeId.Date,
    FieldTypeId.Json,
    FieldTypeId.Enum
  )

  private def isOfStaticType(fieldParser: FieldType[_], texts: Traversable[String]): Boolean =
    texts.forall( text =>
      try {
        fieldParser.parse(text)
        true
      } catch {
        case e: AdaConversionException => false
      }
    )

  override def apply(values: Traversable[String]): FieldType[_] = {
    // first infer static types
    val inferredStaticTypes = nonDefaultTypes.filter { fieldType =>
      isOfStaticType(fieldType, values)
    }

    // then dynamic types and merge
    val inferredDynamicTypes = dynamicFieldInferrers.map(_.apply(values)).flatten

    val inferredTypes = inferredStaticTypes ++ inferredDynamicTypes

    val fieldType = inferredTypes.size match {
      case 0 => defaultType
      case 1 => inferredTypes.head
      case _ => {
        val fieldType = prioritizedFieldTypes.view.map( fieldTypeId =>
          inferredTypes.find(_.spec.fieldType == fieldTypeId)
        ).find(_.isDefined)

        fieldType match {
          case Some(fieldType) => fieldType.get
          case None => inferredStaticTypes.head
        }
      }
    }
    fieldType
  }
}