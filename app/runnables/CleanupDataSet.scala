package runnables

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date

import persistence.RepoTypeRegistry._
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.DeNoPaSetting._

import scala.concurrent.Await
import scala.concurrent.duration._

// TODO: replace MetaTypeStatsRepo with DictionaryRepo
abstract class CleanupDataSet (
    dataRepo: JsObjectCrudRepo,
    curatedDataRepo : JsObjectCrudRepo,
    typeStatsRepo : MetaTypeStatsRepo,
    translationRepo : TranslationRepo
  ) extends Runnable {

  val timeout = 120000 millis

  object InferredType extends Enumeration {
    val Null, Date, Boolean, NumberEnum, FreeNumber, StringEnum, FreeString = Value
  }

  override def run = {
    // remove the curated records from the collection
    Await.result(curatedDataRepo.deleteAll, timeout)

    val translationMap = Await.result(translationRepo.find(), timeout).map(translation =>
      (translation.original, translation.translated)
    ).toMap

    val baselineAttributeTypeMap = getAttributeTypeMap

    val cleanedupBaselineItems = cleanupItems(baselineAttributeTypeMap, translationMap)

    cleanedupBaselineItems.foreach { item =>
      val future = curatedDataRepo.save(item)
      // wait for the execution to complete, i.e., synchronize
      Await.result(future, timeout)
    }
  }

  protected def cleanupItems(
    attributeTypeMap : Map[String, InferredType.Value],
    translationMap : Map[String, String]
  ) = {
    val itemsFuture = dataRepo.find(None, Some(Json.obj("_id" -> 1)))

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

  protected def getAttributeTypeMap : Map[String, InferredType.Value] = {
    val statsFuture = typeStatsRepo.find(None, Some(Json.obj("attributeName" -> 1)))

    Await.result(statsFuture, timeout).map{ item =>
      val valueFreqsWoNa = item.valueRatioMap.filterNot(_._1.toLowerCase.equals("na"))
      val valuesWoNa = valueFreqsWoNa.keySet
      val freqsWoNa = valueFreqsWoNa.values.toSeq

      val inferredType =
        if (typeInferenceProvider.isNull(valuesWoNa))
          InferredType.Null
        else if (typeInferenceProvider.isBoolean(valuesWoNa))
          InferredType.Boolean
        else if (typeInferenceProvider.isDate(valuesWoNa))
          InferredType.Date
        else if (typeInferenceProvider.isNumberEnum(valuesWoNa, freqsWoNa))
          InferredType.NumberEnum
        else if (typeInferenceProvider.isNumber(valuesWoNa))
          InferredType.FreeNumber
        else if (typeInferenceProvider.isTextEnum(valuesWoNa, freqsWoNa))
          InferredType.StringEnum
        else
          InferredType.FreeString

      (item.attributeName, inferredType)
    }.toMap
  }

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
}