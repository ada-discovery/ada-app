package standalone

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date
import javax.inject.Inject

import models.MetaTypeStats
import play.api.libs.json.{JsValue, JsObject, JsNull, Json}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.duration._
import persistence.{DeNoPaBaselineRepo, DeNoPaFirstVisitRepo, AsyncReadonlyRepo, DeNoPaBaselineMetaTypeStatsRepo, DeNoPaFirstVisitMetaTypeStatsRepo}
import scala.concurrent.Await

class DeNoPaCleanup @Inject() (
    baselineRepo: DeNoPaBaselineRepo,
    firstVisitRepo: DeNoPaFirstVisitRepo,
    baselineTypeStatsRepo : DeNoPaBaselineMetaTypeStatsRepo,
    firstVisitTypeStatsRepo : DeNoPaFirstVisitMetaTypeStatsRepo
  ) extends Runnable {

  val timeout = 120000 millis

  val nullAliases = List("na")
  val textBooleanValues = List("ja", "nein", "falsch", "richtig")
  val numBooleanValues = List("0", "1")

  val textBooleanMap = Map("nein" -> false, "ja" -> true, "falsch" -> false, "richtig" -> true)
  val numBooleanMap = Map("0" -> false, "1" -> true)

  val dateFormats = List("yyyy-MM-dd", "dd.MM.yyyy", "MM.yyyy")

  val enumValuesThreshold = 20
  val enumFrequencyThreshold = 0.02

  object InferredType extends Enumeration {
    val Null, Date, Boolean, NumberEnum, FreeNumber, StringEnum, FreeString = Value
  }

  override def run = {
    val baselineAttributeTypeMap = getAttributeTypeMap(baselineTypeStatsRepo)
    val firstVisitAttributeTypeMap = getAttributeTypeMap(firstVisitTypeStatsRepo)

    val cleanedupBaselineItems = cleanupItems(baselineRepo, baselineAttributeTypeMap)
    val cleanedupFirstVisitItems = cleanupItems(firstVisitRepo, firstVisitAttributeTypeMap)

    println("Base line")
    println("---------")
    println(cleanedupBaselineItems.size)
    println
    println("First visit")
    println("-----------")
    println(cleanedupFirstVisitItems.size)
  }

  private def cleanupItems(
    repo : AsyncReadonlyRepo[JsObject, BSONObjectID],
    attributeTypeMap : Map[String, InferredType.Value]
  ) = {
    val itemsFuture = repo.find(None, Some(Json.obj("_id" -> 1)))

    val convertedJsItems = Await.result(itemsFuture, timeout).map { item =>
      val newFieldValues : Seq[(String, JsValue)] = item.fields.filter(_._1 != InferredType.Null).map{ case (attribute, jsValue) =>
        if (jsValue == JsNull)
          (attribute, JsNull)
        else {
          val string = jsValue.as[String]
          if (nullAliases.contains(string.toLowerCase))
            (attribute, JsNull)
          else {
            val convertedValue = attributeTypeMap.get(attribute).get match {
              case InferredType.Null => JsNull
              case InferredType.Date => Json.toJson(toDate(string))
              case InferredType.Boolean => Json.toJson(toBoolean(string))
              case InferredType.NumberEnum => Json.toJson(string.toDouble)
              case InferredType.FreeNumber => Json.toJson(string.toDouble)
              case InferredType.StringEnum => Json.toJson(string)
              case InferredType.FreeString => Json.toJson(string)
            }
            (attribute, convertedValue)
          }
        }
      }
      JsObject(newFieldValues)
    }

    // remove all items without any content
    convertedJsItems.filter(item => item.values.exists(_ != JsNull))
  }

  private def getAttributeTypeMap(repo : AsyncReadonlyRepo[MetaTypeStats, BSONObjectID]) : Map[String, InferredType.Value] = {
    val statsFuture = repo.find(None, Some(Json.obj("attributeName" -> 1)))

    Await.result(statsFuture, timeout).map{ item =>
      val valueFreqsWoNa = item.valueRatioMap.filterNot(_._1.toLowerCase.equals("na"))
      val valuesWoNa = valueFreqsWoNa.keySet
      val freqsWoNa = valueFreqsWoNa.values.toSeq

      val inferredType =
        if (isNullOrNA(valuesWoNa))
          InferredType.Null
        else if (isBoolean(valuesWoNa))
          InferredType.Boolean
        else if (isDate(valuesWoNa))
          InferredType.Date
        else if (isNumberEnum(valuesWoNa, freqsWoNa))
          InferredType.NumberEnum
        else if (isNumber(valuesWoNa))
          InferredType.FreeNumber
        else if (isTextEnum(valuesWoNa, freqsWoNa))
          InferredType.StringEnum
        else
          InferredType.FreeString

      (item.attributeName, inferredType)
    }.toMap
  }

  private def isNullOrNA(valuesWoNA : Set[String]) =
    valuesWoNA.size == 0

  private def isNumber(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => s.forall(c => c.isDigit || c == '.') && s.count(_ == '.') <= 1)

  private def isDate(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => dateFormats.exists { format =>
      try {
        new SimpleDateFormat(format).parse(s)
        true
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

  def dateExpectedException(string : String) = new IllegalArgumentException(s"String ${string} is expected to be date-convertible but it's not.")
  def booleanExpectedException(string : String) = new IllegalArgumentException(s"String ${string} is expected to be boolean-convertible but it's not.")

  private def toDate(string : String) : Date = {
    val dates = dateFormats.map{ format =>
      try {
        val date = new SimpleDateFormat(format).parse(string)
        Some(date)
      } catch {
        case e: ParseException => None
      }
    }.flatten

    if (dates.isEmpty)
      try {
        val year = string.toInt
        if (year > 1900 && year < 2100)
          new SimpleDateFormat(string).parse("01.01." + string)
        else
          throw dateExpectedException(string)
      } catch {
        case t: NumberFormatException => throw dateExpectedException(string)
      }
    else
      dates(0)
  }

  private def toBoolean(string : String) : Boolean =
    (textBooleanMap ++ numBooleanMap).getOrElse(string, throw booleanExpectedException(string))

  private def isBoolean(valuesWoNA : Set[String]) =
    isTextBoolean(valuesWoNA) || isNumberBoolean(valuesWoNA)

  private def isTextBoolean(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => textBooleanValues.contains(s.toLowerCase))

  private def isNumberBoolean(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => numBooleanValues.contains(s)) //    isNumber(valuesWoNA) && valuesWoNA.size <= 2

  private def isEnum(freqsWoNa : Seq[Double]) =
    freqsWoNa.size < enumValuesThreshold && (freqsWoNa.sum / freqsWoNa.size) > enumFrequencyThreshold

  private def isNumberEnum(valuesWoNA : Set[String], freqsWoNa : Seq[Double]) =
    isNumber(valuesWoNA) && isEnum(freqsWoNa)

  private def isTextEnum(valuesWoNA : Set[String], freqsWoNa : Seq[Double]) =
    isEnum(freqsWoNa) && valuesWoNA.exists(_.exists(_.isLetter))
}

object DeNoPaCleanup extends GuiceBuilderRunnable[DeNoPaCleanup] with App {
  override def main(args: Array[String]) = run
}