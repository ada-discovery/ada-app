package standalone

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date
import javax.inject.Inject

import models.MetaTypeStats
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID

import scala.concurrent.duration._
import persistence._
import scala.concurrent.Await

class DeNoPaCleanup @Inject() (
    baselineRepo: DeNoPaBaselineRepo,
    firstVisitRepo: DeNoPaFirstVisitRepo,
    curatedBaselineRepo : DeNoPaCuratedBaselineRepo,
    curatedFirstVisitRepo: DeNoPaCuratedFirstVisitRepo,
    translationRepo : TranslationRepo,
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
  val storeDateFormat =  new SimpleDateFormat("dd.MM.yyyy")

  val enumValuesThreshold = 20
  val enumFrequencyThreshold = 0.02

  object InferredType extends Enumeration {
    val Null, Date, Boolean, NumberEnum, FreeNumber, StringEnum, FreeString = Value
  }

  override def run = {
    // remove the curated baseline records from the collection
    Await.result(curatedBaselineRepo.deleteAll, timeout)

    // remove the curated first visit records from the collection
    Await.result(curatedFirstVisitRepo.deleteAll, timeout)

    val translationMap = Await.result(translationRepo.find(), timeout).map(translation =>
      (translation.original, translation.translated)
    ).toMap

    val baselineAttributeTypeMap = getAttributeTypeMap(baselineTypeStatsRepo)
    val firstVisitAttributeTypeMap = getAttributeTypeMap(firstVisitTypeStatsRepo)

    val cleanedupBaselineItems = cleanupItems(baselineRepo, baselineAttributeTypeMap, translationMap)
    val cleanedupFirstVisitItems = cleanupItems(firstVisitRepo, firstVisitAttributeTypeMap, translationMap)

    cleanedupBaselineItems.foreach { item =>
      val future = curatedBaselineRepo.save(item)
      // wait for the execution to complete, i.e., synchronize
      Await.result(future, timeout)
    }

    cleanedupFirstVisitItems.foreach { item =>
      val future = curatedFirstVisitRepo.save(item)
      // wait for the execution to complete, i.e., synchronize
      Await.result(future, timeout)
    }

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
    attributeTypeMap : Map[String, InferredType.Value],
    translationMap : Map[String, String]
  ) = {
    val itemsFuture = repo.find(None, Some(Json.obj("_id" -> 1)))

    val convertedJsItems = Await.result(itemsFuture, timeout).map { item =>
      val newFieldValues: Seq[(String, JsValue)] = item.fields.filter{case (attribute, value) =>
        attribute == "_id" || attributeTypeMap.get(attribute).get != InferredType.Null
      }.map { case (attribute, jsValue) =>
        jsValue match {
          case JsNull => (attribute, JsNull)
          case id: BSONObjectID => ("orig_id", id)
          case _: JsString => {
            val string = jsValue.asOpt[String].getOrElse(
              throw new IllegalArgumentException(jsValue + " is not String.")
            )
            if (nullAliases.contains(string.toLowerCase)) {
              (attribute, JsNull)
            } else {
              val convertedValue = attributeTypeMap.get(attribute).get match {
                case InferredType.Null => JsNull
                case InferredType.Date => Json.toJson(storeDateFormat.format(toDate(string)))
                case InferredType.Boolean => Json.toJson(toBoolean(string))
                case InferredType.NumberEnum => toJsonNum(string)
                case InferredType.FreeNumber => toJsonNum(string)
                case InferredType.StringEnum => Json.toJson(translationMap.get(string).getOrElse(string))
                case InferredType.FreeString => Json.toJson(translationMap.get(string).getOrElse(string))
              }
              (attribute, convertedValue)
            }
          }
          case _ => (attribute, jsValue)
        }
      }
      JsObject(newFieldValues)
    }

    // remove all items without any content
    convertedJsItems.filter(item => item.fields.exists{ case (attribute, value) => attribute != "Line_Nr" && attribute != "orig_id" && attribute != "_id" && value != JsNull})
  }

  private def toJsonNum(string : String) = {
    try {
      Json.toJson(string.toLong)
    } catch {
      case t: NumberFormatException => Json.toJson(string.toDouble)
    }
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
          new SimpleDateFormat("dd.MM.yyyy").parse("01.01." + string)
        else
          throw dateExpectedException(string)
      } catch {
        case t: NumberFormatException => throw dateExpectedException(string)
      }
    else
      dates(0)
  }

  private def toBoolean(string : String) : Boolean =
    (textBooleanMap ++ numBooleanMap).getOrElse(string.toLowerCase, throw booleanExpectedException(string))

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