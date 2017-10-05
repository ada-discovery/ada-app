package dataaccess

import java.text.SimpleDateFormat
import java.util.Date

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import models.{FieldTypeSpec, FieldTypeId}
import play.api.libs.json._
import java.{util => ju}
import java.{lang => jl}
import dataaccess.ConversionUtil._

import scala.reflect.ClassTag

trait FieldTypeFactory {
  def apply(field: FieldTypeSpec): FieldType[_]

  def apply(
    fieldType: FieldTypeId.Value,
    isArray: Boolean = false,
    enumValues: Option[Map[Int, String]] = None
  ): FieldType[_] =
    apply(FieldTypeSpec(fieldType, isArray, enumValues))

  def allStaticTypes: Traversable[FieldType[_]]

  // some common type shortcuts

  def stringScalar = apply(FieldTypeId.String).asValueOf[String]
  def stringArray = apply(FieldTypeId.String, true).asValueOf[Array[Option[String]]]
}

// Enum
private case class EnumFieldType(
    val nullAliases: Set[String],
    enumValueMap: Map[Int, String]
  ) extends FormatFieldType[Int] {

  val spec = FieldTypeSpec(FieldTypeId.Enum, false, Some(enumValueMap))

  override val valueClass = classOf[Int]

  override val classTag = Some(implicitly[ClassTag[Int]])

  val reversedEnumValueMap = enumValueMap.map(_.swap)

  override protected def displayStringToValueWoNull(text: String) =
    reversedEnumValueMap.get(text).getOrElse(
      throw new AdaConversionException(s"'$text' is not valid enum label: ${enumValueMap.mkString(";")}.")
    )

  override protected def valueStringToValueWoNull(text: String) =
    toInt(text)

  override protected def valueToDisplayStringNonEmpty(value: Int) =
    enumValueMap.get(value).getOrElse(
      throw new AdaConversionException(s"Number '$value' is not valid enum value: ${enumValueMap.mkString(";")}.")
    )

  override protected def jsonToValueWoNull(json: JsReadable): Int =
    json match {
      case JsNumber(number) =>
        try {
          val int = number.toIntExact
          if (enumValueMap.contains(int))
            int
          else
            throw new AdaConversionException(s"Number '$number' is not valid enum value: ${enumValueMap.mkString(";")}.")
        } catch {
          case e: ArithmeticException => throw new AdaConversionException(s"Number '$number' is not valid enum value: ${enumValueMap.mkString(";")}.")
        }
      case _ => throw new AdaConversionException(s"Json $json is not enum's int.")
    }

  override protected def valueToJsonNonEmpty(value: Int): JsValue =
    if (enumValueMap.contains(value))
      super.valueToJsonNonEmpty(value)
    else
      throw new AdaConversionException(s"'$value' is not valid enum value: ${enumValueMap.mkString(";")}.")
}

// Double
private case class DoubleFieldType(
    val nullAliases: Set[String],
    displayDecimalPlaces: Option[Int] = None
  ) extends  FormatFieldType[Double] {

  val spec = FieldTypeSpec(FieldTypeId.Double, false)

  override val valueClass = classOf[jl.Double]

  override val classTag = Some(implicitly[ClassTag[Double]])

  override protected def displayStringToValueWoNull(text: String) =
    toDouble(text)

  override protected def valueToDisplayStringNonEmpty(value: Double) =
    displayDecimalPlaces.map( places =>
      BigDecimal(value).setScale(
        places,
        BigDecimal.RoundingMode.HALF_UP
      ).toString
    ).getOrElse(value.toString)

  override protected def displayJsonToValueWoString(json: JsReadable) =
    json match {
      case JsNumber(number) =>
        try {
          number.toDouble
        } catch {
          case e: ArithmeticException => throw new AdaConversionException(s"Json $json is not double.")
        }
        case  _ => throw new AdaConversionException(s"Json $json is not double.")
    }

  override protected def jsonToValueWoNull(json: JsReadable) =
    displayJsonToValueWoString(json)
}

// Boolean
private case class BooleanFieldType(
    val nullAliases: Set[String],
    includeNumbers: Boolean = true,
    displayTrueValue: Option[String] = None,
    displayFalseValue: Option[String] = None
  ) extends FormatFieldType[Boolean] {

  val spec = FieldTypeSpec(FieldTypeId.Boolean, false)

  override val valueClass = classOf[jl.Boolean]

  override val classTag = Some(implicitly[ClassTag[Boolean]])

  override protected def displayStringToValueWoNull(text: String) =
    toBoolean(includeNumbers)(text)

  override protected def valueToDisplayStringNonEmpty(value: Boolean) = {
    val displayValue = if (value)
      displayTrueValue
    else
      displayFalseValue

    displayValue.getOrElse(value.toString)
  }

  override protected def displayJsonToValueWoString(json: JsReadable) =
    json match {
      case JsBoolean(boolean) => boolean
      case JsNumber(number) => toBoolean(includeNumbers)(number.toString)  // we accept also number as a display Json
      case  _ => throw new AdaConversionException(s"Json $json cannot be converted to a Boolean.")
    }

  override protected def jsonToValueWoNull(json: JsReadable) =
    json match {
      case JsBoolean(boolean) => boolean
      case  _ => throw new AdaConversionException(s"Json $json is not boolean.")
    }
}

private case class ArrayFieldType[T](
    elementFieldType: FieldType[T],
    delimiter: String
  ) extends FieldType[Array[Option[T]]] {

  val elementFieldTypeSpec = elementFieldType.spec
  val spec = FieldTypeSpec(elementFieldTypeSpec.fieldType, true, elementFieldTypeSpec.enumValues)

  override protected[dataaccess] val nullAliases: Set[String] = elementFieldType.nullAliases

  override val valueClass = classOf[Array[Any]]

  override val classTag = None

  private implicit val elementClassTag: ClassTag[T] =
    elementFieldType.classTag.getOrElse(throw new IllegalArgumentException(s"No class tag provided for $elementFieldTypeSpec."))

  override protected def displayStringToValueWoNull(text: String) = {
    val values: Seq[Option[T]] = text.split(delimiter, -1).map(textElement =>
      elementFieldType.displayStringToValue(textElement.trim))
    values.toArray
  }

  override protected def valueStringToValueWoNull(text: String): Array[Option[T]] = {
    val values: Seq[Option[T]] = text.split(delimiter, -1).map(textElement =>
      elementFieldType.valueStringToValue(textElement.trim))
    values.toArray
  }

  override protected def displayJsonToValueWoString(json: JsReadable) =
    json match {
      case JsArray(seq) => {
        val values: Seq[Option[T]] = seq.map(elementFieldType.displayJsonToValue)
        values.toArray
      }
      case _ => throw new AdaConversionException(s"JSON $json is not an array.")
    }

  override protected def valueToDisplayStringNonEmpty(array: Array[Option[T]]) =
    array.map( elementValue =>
      elementFieldType.valueToDisplayString(elementValue)
    ).mkString(delimiter)

  override protected def jsonToValueWoNull(json: JsReadable): Array[Option[T]] =
    json match {
      case JsArray(array) =>
        array.map( elementJson =>
          elementFieldType.jsonToValue(elementJson)
        ).toArray
      case _ => throw new AdaConversionException(s"JSON $json is not an array.")
    }

  override def valueToJsonNonEmpty(array: Array[Option[T]]): JsValue = {
    val jsValues = array.map(elementFieldType.valueToJson)
    JsArray(jsValues)
  }
}

object FieldTypeFactory {
  def apply(
    nullAliases: Set[String],
    dateFormats: Traversable[String],
    displayDateFormat: String,
    arrayDelimiter: String,
    booleanIncludeNumbers: Boolean
  ): FieldTypeFactory = new FieldTypeFactoryImpl(nullAliases, dateFormats, displayDateFormat, arrayDelimiter, booleanIncludeNumbers)
}

private class FieldTypeFactoryImpl(
    nullValues: Set[String],
    dateFormats: Traversable[String],
    displayDateFormat: String,
    arrayDelimiter: String,
    booleanIncludeNumbers: Boolean
  ) extends FieldTypeFactory {

  private val staticScalarTypes: Seq[FieldType[_]] = Seq(
    // Null
    new FieldType[Nothing] {
      val spec = FieldTypeSpec(FieldTypeId.Null, false)

      val valueClass = classOf[Nothing]

      override val classTag = Some(implicitly[ClassTag[Nothing]])

      override protected[dataaccess] val nullAliases = nullValues

      override protected def displayStringToValueWoNull(text: String) =
        throw new AdaConversionException(s"$text is not null.")

      override protected def displayJsonToValueWoString(json: JsReadable) =
        throw new AdaConversionException(s"$json is not null.")

      override protected def valueToDisplayStringNonEmpty(value: Nothing) =
        ""

      override protected def jsonToValueWoNull(json: JsReadable) =
        throw new AdaConversionException(s"$json is not null.")

      override protected def valueToJsonNonEmpty(value: Nothing) = JsNull
    },

    // Integer
    new FormatFieldType[Long] {
      val spec = FieldTypeSpec(FieldTypeId.Integer, false)

      val valueClass = classOf[jl.Long]

      override val classTag = Some(implicitly[ClassTag[Long]])

      override protected[dataaccess] val nullAliases = nullValues

      override protected def displayJsonToValueWoString(json: JsReadable) =
        json match {
          case JsNumber(number) =>
            try {
              number.toLongExact
            } catch {
              case e: ArithmeticException => throw new AdaConversionException(s"Json $json is not long.")
            }
          case  _ => throw new AdaConversionException(s"Json $json is not long.")
        }

      override protected def jsonToValueWoNull(json: JsReadable) =
        displayJsonToValueWoString(json)

      override protected def displayStringToValueWoNull(text: String) =
        toLong(text)
    },

    // Date
    new FormatFieldType[ju.Date] {
      val spec = FieldTypeSpec(FieldTypeId.Date, false)

      val valueClass = classOf[ju.Date]

      override val classTag = Some(implicitly[ClassTag[ju.Date]])

      private val displayFormatter = new SimpleDateFormat(displayDateFormat)

      override protected[dataaccess] val nullAliases = nullValues

      override protected def displayStringToValueWoNull(text: String) =
        try {
          toDate(dateFormats)(text)
        } catch {
          // as a failover we try to interpret it as milliseconds
          case e: AdaConversionException => toDateFromMsString(text)
        }

      override protected def displayJsonToValueWoString(json: JsReadable) =
        json match {
          case JsNumber(number) => toDateFromMs(number.toLong)
          case  _ => throw new AdaConversionException(s"Json $json cannot be converted to a Date.")
        }

      override protected def valueStringToValueWoNull(text: String) =
        toDate(Seq(displayDateFormat))(text)

      override protected def jsonToValueWoNull(json: JsReadable) =
        json match {
          case JsNumber(number) => new ju.Date(number.toLong)
          case  _ => throw new AdaConversionException(s"Json $json cannot be converted to a Date.")
        }

      override protected def valueToDisplayStringNonEmpty(date: Date) =
        displayFormatter.format(date)
    },

    // String
    new FormatFieldType[String] {
      val spec = FieldTypeSpec(FieldTypeId.String, false)

      val valueClass = classOf[String]

      override val classTag = Some(implicitly[ClassTag[String]])

      override protected[dataaccess] val nullAliases = nullValues

      override protected def displayStringToValueWoNull(text: String) =
        text

      override protected def displayJsonToValueWoString(json: JsReadable) =
        json match {
          case JsNumber(number) => number.toString
          case JsBoolean(value) => value.toString
          case  _ => throw new AdaConversionException(s"Json $json is not String.")
        }

      override protected def jsonToValueWoNull(json: JsReadable) =
        json match {
          case JsString(text) => text
          case  _ => throw new AdaConversionException(s"Json $json is not a String.")
        }
    },

    // Json
    new FormatFieldType[JsObject] {
      val spec = FieldTypeSpec(FieldTypeId.Json, false)

      val valueClass = classOf[JsObject]

      override val classTag = Some(implicitly[ClassTag[JsObject]])

      override protected[dataaccess] val nullAliases = nullValues

      override protected def displayStringToValueWoNull(text: String) =
        try {
          Json.parse(text) match {
            case x: JsObject => x
            case _ => throw new AdaConversionException(s"$text cannot be parsed to a JSON.")
          }
        } catch {
          case e: JsonParseException => throw new AdaConversionException(s"$text cannot be parsed to a JSON.")
          case e: JsonMappingException => throw new AdaConversionException(s"$text cannot be parsed to a JSON.")
        }

      override protected def valueToDisplayStringNonEmpty(json: JsObject) =
        Json.stringify(json)

      override def displayJsonToValueWoString(json: JsReadable): JsObject =
        json match {
          case x: JsObject => x
          case _ => throw new AdaConversionException(s"$json cannot be parsed to a JSON.")
        }

      override protected def jsonToValueWoNull(json: JsReadable) =
        displayJsonToValue(json).get
    }
  )

  private val staticJsonArrayType = new FieldType[Array[Option[JsObject]]] {

    val spec = FieldTypeSpec(FieldTypeId.Json, true)

    val valueClass = classOf[Array[Any]]

    override val classTag = None

    override protected[dataaccess] val nullAliases = nullValues

    override protected def displayStringToValueWoNull(text: String) =
      try {
        val json = Json.parse(text)
        json match {
          case JsArray(value) => value.map(toOption).toArray
          case _ => Array(toOption(json))
        }
      } catch {
        case e: JsonParseException => throw new AdaConversionException(s"$text cannot be parsed to a JSON array.")
        case e: JsonMappingException => throw new AdaConversionException(s"$text cannot be parsed to a JSON array.")
      }

    override protected def valueStringToValueWoNull(text: String) =
      displayStringToValueWoNull(text)

    override protected def valueToDisplayStringNonEmpty(jsons: Array[Option[JsObject]]) =
      Json.stringify(JsArray(jsons.map(fromOption)))

    override def displayJsonToValue(json: JsReadable): Option[Array[Option[JsObject]]] =
      json match {
        case JsNull => None
        case JsDefined(json) => displayJsonToValue(json)
        case _: JsUndefined => None
        case JsArray(array) => Some(array.map(toOption).toArray)
        case x: JsObject => Some(Array(Some(x)))
        case _ => throw new AdaConversionException(s"$json is not a JSON array.")
      }

    override protected def jsonToValueWoNull(json: JsReadable) =
      json match {
        case JsArray(array) => array.map(toOption).toArray
        case _ => throw new AdaConversionException(s"$json is not a JSON array.")
      }

    override protected def valueToJsonNonEmpty(value: Array[Option[JsObject]]) =
      JsArray(value.map(fromOption))

    private def toOption(value: JsValue): Option[JsObject] =
      value match  {
        case JsNull => None
        case x: JsObject => Some(x)
        case _ =>  throw new AdaConversionException(s"$value is not a JSON object.")
      }

    private def fromOption(value: Option[JsObject]): JsValue =
      value match  {
        case None => JsNull
        case Some(value) => value
      }
  }

  private val staticArrayTypes: Seq[FieldType[_]] = staticScalarTypes.map(scalarType => ArrayFieldType(scalarType, arrayDelimiter)) ++ Seq(staticJsonArrayType)

  private val staticTypes: Seq[FieldType[_]] = staticScalarTypes ++ staticArrayTypes

  private val staticTypeMap: Map[FieldTypeSpec, FieldType[_]] = staticTypes.map(fType => (fType.spec, fType)).toMap

  override def apply(fieldTypeSpec: FieldTypeSpec): FieldType[_] = {
    val fieldTypeId = fieldTypeSpec.fieldType

    def arrayOrElseScalar(scalarType: FieldType[_]) =
      if (fieldTypeSpec.isArray)
        ArrayFieldType(scalarType, arrayDelimiter)
      else
        scalarType

    // handle dynamic types (e.g. enum)
    fieldTypeId match {
      case FieldTypeId.Enum => {
        val intEnumMap = fieldTypeSpec.enumValues.getOrElse(Map[Int, String]())
        val scalarType = EnumFieldType(nullValues, intEnumMap)
        arrayOrElseScalar(scalarType)
      }

      case FieldTypeId.Double => {
        val scalarType = DoubleFieldType(nullValues, fieldTypeSpec.displayDecimalPlaces)
        arrayOrElseScalar(scalarType)
      }

      case FieldTypeId.Boolean => {
        val scalarType = BooleanFieldType(nullValues, booleanIncludeNumbers, fieldTypeSpec.displayTrueValue, fieldTypeSpec.displayFalseValue)
        arrayOrElseScalar(scalarType)
      }

      case _ => {
        // get a static type
        staticTypeMap.getOrElse(fieldTypeSpec,
          throw new IllegalArgumentException(s"Field type $fieldTypeSpec unrecognized.")
        )
      }
    }
  }

  override def allStaticTypes: Traversable[FieldType[_]] = staticTypes
}