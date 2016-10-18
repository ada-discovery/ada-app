package dataaccess

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import play.api.libs.json._
import java.{util => ju}
import java.{lang => jl}
import dataaccess.ConversionUtil._

trait FieldTypeFactory {
  def apply(field: FieldTypeSpec): FieldType[_]
  def allStaticTypes: Traversable[FieldType[_]]
}

private case class EnumFieldType(
    val nullAliases: Set[String],
    enumValuesMaxCount: Int,
    enumValueMap: Map[Int, String]
  ) extends FormatFieldType[Int] {

  val spec = FieldTypeSpec(FieldTypeId.Enum, false, Some(enumValueMap.map{ case (from, to) => (from.toString, to)}))
  val valueClass = classOf[Int]
  val reversedEnumValueMap = enumValueMap.map(_.swap)

  protected def parseWoNull(text: String) =
    reversedEnumValueMap(text)
}

object FieldTypeFactory {
  def apply(
    nullAliases: Set[String],
    dateFormats: Traversable[String],
    enumValuesMaxCount: Int
//    textBooleanValues : List[String],
//    numBooleanValues  : List[String],
  ): FieldTypeFactory = new FieldTypeFactoryImpl(nullAliases, dateFormats, enumValuesMaxCount)
}

private class FieldTypeFactoryImpl(
    nullValues: Set[String],
    dateFormats: Traversable[String],
    enumValuesMaxCount: Int
  ) extends FieldTypeFactory {

  private val staticTypes = Seq(
    // Null
    new FieldType[Any] {
      val spec = FieldTypeSpec(FieldTypeId.Null, false)
      val valueClass = classOf[String]
      protected val nullAliases = nullValues

      protected def parseWoNull(text: String) =
        throw new AdaConversionException(s"$text in not null.")

      def toJson(value: Any) = JsNull
    },

    // Integer
    new FormatFieldType[Long] {
      val spec = FieldTypeSpec(FieldTypeId.Integer, false)
      val valueClass = classOf[jl.Long]
      protected val nullAliases = nullValues

      protected def parseWoNull(text: String) = toLong(text)
    },

    // Double
    new FormatFieldType[Double] {
      val spec = FieldTypeSpec(FieldTypeId.Double, false)
      val valueClass = classOf[jl.Double]
      protected val nullAliases = nullValues

      protected def parseWoNull(text: String) = toDouble(text)
    },

    // Boolean
    new FormatFieldType[Boolean] {
      val spec = FieldTypeSpec(FieldTypeId.Boolean, false)
      val valueClass = classOf[jl.Boolean]
      protected val nullAliases = nullValues

      protected def parseWoNull(text: String) = toBoolean(text)
    },

    // Date
    new FormatFieldType[ju.Date] {
      val spec = FieldTypeSpec(FieldTypeId.Date, false)
      val valueClass = classOf[ju.Date]
      protected val nullAliases = nullValues

      protected def parseWoNull(text: String) = toDate(dateFormats)(text)
    },

    // String
    new FormatFieldType[String] {
      val spec = FieldTypeSpec(FieldTypeId.String, false)
      val valueClass = classOf[String]
      protected val nullAliases = nullValues

      protected def parseWoNull(text: String) = text
    },

    // Json
    new FormatFieldType[JsValue] {
      val spec = FieldTypeSpec(FieldTypeId.Json, false)
      val valueClass = classOf[JsValue]
      protected val nullAliases = nullValues

      protected def parseWoNull(text: String) =
        try {
          Json.parse(text)
        } catch {
          case e: JsonParseException => throw new AdaConversionException("Json expected")
          case e: JsonMappingException => throw new AdaConversionException("Json expected")
        }
    }
  )

  private val staticTypeMap = staticTypes.map(fType => (fType.spec, fType)).toMap

  override def apply(fieldTypeSpec: FieldTypeSpec) = {
    val fieldTypeId = fieldTypeSpec.fieldType
    // handle dynamic types (e.g. enum)
    if (fieldTypeId == FieldTypeId.Enum) {
      val intEnumMap = fieldTypeSpec.enumValues.map(
        _.map { case (from, to) => (from.toInt, to) }
      )
      EnumFieldType(nullValues, enumValuesMaxCount, intEnumMap.getOrElse(Map[Int, String]()))
    } else {

      // if not successful get static type
      staticTypeMap.getOrElse(fieldTypeSpec,
        throw new IllegalArgumentException(s"Field type id $fieldTypeId unrecognized.")
      )
    }
  }

  override def allStaticTypes: Traversable[FieldType[_]] = staticTypes
}