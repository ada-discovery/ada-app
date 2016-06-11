package util

import java.text.{ParseException, SimpleDateFormat}
import com.fasterxml.jackson.core.JsonParseException

import collection.mutable.{Map => MMap}
import play.api.libs.json.Json

import models.FieldType

case class TypeInferenceProvider(
    nullAliases : List[String],
    textBooleanValues : List[String],
    numBooleanValues  : List[String],
    dateFormats : Traversable[String],
    enumValuesThreshold : Int,
    enumFrequencyThreshold : Double
  ) {

  def isNull(values : Traversable[String]) =
    values.forall(s => nullAliases.contains(s.toLowerCase))

  def isBoolean(valuesWoNA : Traversable[String]) =
    isTextBoolean(valuesWoNA) || isNumberBoolean(valuesWoNA)

  def isTextBoolean(valuesWoNA : Traversable[String]) =
    valuesWoNA.forall(s => textBooleanValues.contains(s.toLowerCase))

  def isNumberBoolean(valuesWoNA : Traversable[String]) =
    valuesWoNA.forall(s => numBooleanValues.contains(s)) //    isNumber(valuesWoNA) && valuesWoNA.size <= 2

  def isEnum(valuesWoNull : Traversable[String]): Boolean = {
    val countMap = MMap[String, Int]()
    valuesWoNull.foreach{value =>
      val count = countMap.getOrElse(value, 0)
      countMap.update(value, count + 1)
    }

    val sum = countMap.values.sum
    val freqsWoNa = countMap.values.map(count => count.toDouble / sum)
    isEnumForFreq(freqsWoNa)
  }

  def isEnumForFreq(freqsWoNa : Traversable[Double]): Boolean =
    freqsWoNa.size < enumValuesThreshold && (freqsWoNa.sum / freqsWoNa.size) > enumFrequencyThreshold

  def isNumberEnum(valuesWoNA : Traversable[String], freqsWoNa : Traversable[Double]) =
    isNumber(valuesWoNA) && isEnumForFreq(freqsWoNa)

  def isTextEnum(valuesWoNA : Traversable[String], freqsWoNa : Traversable[Double]) =
    isEnumForFreq(freqsWoNa) && valuesWoNA.exists(_.exists(_.isLetter))

  def isNumber(valuesWoNA : Traversable[String]) =
    valuesWoNA.forall(s => s.forall(c => c.isDigit || c == '.') && s.count(_ == '.') <= 1)

  def isDouble(valuesWoNA : Traversable[String]) : Boolean =
    valuesWoNA.forall(isDouble)

  def isInt(valuesWoNA : Traversable[String]) : Boolean =
    valuesWoNA.forall(isInt)

  def isDate(valuesWoNA : Traversable[String]) =
    valuesWoNA.forall(s => dateFormats.exists { format =>
      try {
        val date = new SimpleDateFormat(format).parse(s)
        val year1900 = date.getYear
        year1900 > 0 && year1900 < 200
      } catch {
        case e: ParseException => false
      }
    } || {
      try {
        val year = s.toInt
        year > 1900 && year < 2100
      } catch {
        case t: NumberFormatException => false
      }
    }
   )

  def isJson(values : Traversable[String]) =
    values.forall(s => try {
      Json.parse(s)
      true
    } catch {
      case e: JsonParseException => false
    })

  def getType(values : Traversable[String]) = {
    val valuesWoNull = values.filterNot(value => nullAliases.contains(value.toLowerCase))
//    val uniqueValuesWoNull = values.toSet.filterNot(value => nullAliases.contains(value.toLowerCase))

    if (isNull(values))
      FieldType.Null
    else if (isBoolean(valuesWoNull))
      FieldType.Boolean
    else if (isDate(valuesWoNull))
      FieldType.Date
    else if (isInt(valuesWoNull))
      FieldType.Integer
    else if (isDouble(valuesWoNull))
      FieldType.Double
    else if (isEnum(valuesWoNull))
      FieldType.Enum
    else if (isJson(valuesWoNull))
      FieldType.Json
    else
      FieldType.String
  }

  def isDouble(text : String) =
    try {
      text.toDouble
      true
    } catch {
      case t: NumberFormatException => false
    }

  def isInt(text : String) =
    try {
      text.toInt
      true
    } catch {
      case t: NumberFormatException => false
    }

  def isLong(text : String) =
    try {
      text.toLong
      true
    } catch {
      case t: NumberFormatException => false
    }

  def isFloat(text : String) =
    try {
      text.toFloat
      true
    } catch {
      case t: NumberFormatException => false
    }

  def isBoolean(text : String) =
    try {
      text.toBoolean
      true
    } catch {
      case t: IllegalArgumentException => false
    }
}
