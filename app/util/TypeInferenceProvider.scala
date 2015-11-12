package util

import java.text.{ParseException, SimpleDateFormat}

case class TypeInferenceProvider(
    nullAliases : Traversable[String],
    textBooleanValues : List[String],
    numBooleanValues  : List[String],
    dateFormats : Traversable[String],
    enumValuesThreshold : Int,
    enumFrequencyThreshold : Double
  ) {

  def isBoolean(valuesWoNA : Set[String]) =
    isTextBoolean(valuesWoNA) || isNumberBoolean(valuesWoNA)

  def isTextBoolean(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => textBooleanValues.contains(s.toLowerCase))

  def isNumberBoolean(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => numBooleanValues.contains(s)) //    isNumber(valuesWoNA) && valuesWoNA.size <= 2

  def isEnum(freqsWoNa : Seq[Double]) =
    freqsWoNa.size < enumValuesThreshold && (freqsWoNa.sum / freqsWoNa.size) > enumFrequencyThreshold

  def isNumberEnum(valuesWoNA : Set[String], freqsWoNa : Seq[Double]) =
    isNumber(valuesWoNA) && isEnum(freqsWoNa)

  def isTextEnum(valuesWoNA : Set[String], freqsWoNa : Seq[Double]) =
    isEnum(freqsWoNa) && valuesWoNA.exists(_.exists(_.isLetter))

  def isNullOrNA(valuesWoNA : Set[String]) =
    valuesWoNA.size == 0

  def isNumber(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => s.forall(c => c.isDigit || c == '.') && s.count(_ == '.') <= 1)

  def isDate(valuesWoNA : Set[String]) =
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
