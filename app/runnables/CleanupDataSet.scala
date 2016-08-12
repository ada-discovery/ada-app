package runnables

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date

import javax.inject.Inject

import dataaccess.{FieldType, DataSetSetting, DataSetMetaInfo, AscSort}
import persistence.RepoTypes._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import persistence.dataset.{DataSetAccessorFactory, DataSetAccessor}
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.DeNoPaSetting._
import scala.concurrent.Await
import scala.concurrent.duration._

abstract class CleanupDataSet (
    originalDataSetId: String,
    newDataSetMetaInfo: DataSetMetaInfo,
    newDataSetSetting: Option[DataSetSetting],
    translationRepo : TranslationRepo
  ) extends Runnable {

  @Inject() protected var dsaf: DataSetAccessorFactory = _

  val timeout = 120000 millis

  lazy val originalDsa = dsaf(originalDataSetId).get
  lazy val newDsa = {
    val futureAccessor = for {
      originalDataSetInfo <- originalDsa.metaInfo
      accessor <- dsaf.register(newDataSetMetaInfo.copy(dataSpaceId = originalDataSetInfo.dataSpaceId), newDataSetSetting)
    } yield
      accessor
    Await.result(futureAccessor, timeout)
  }

  lazy val originalDataRepo = originalDsa.dataSetRepo
  lazy val newDataRepo = newDsa.dataSetRepo
  lazy val originalDictionaryRepo = originalDsa.fieldRepo

  override def run = {
    // remove the curated records from the collection
    Await.result(newDataRepo.deleteAll, timeout)

    val translationMap = Await.result(translationRepo.find(), timeout).map(translation =>
      (translation.original, translation.translated)
    ).toMap

    val cleanedupItems = cleanupItems(fieldTypeMap, translationMap)

    cleanedupItems.foreach { item =>
      val future = newDataRepo.save(item)
      // wait for the execution to complete, i.e., synchronize
      Await.result(future, timeout)
    }
  }

  protected def cleanupItems(
    fieldTypeMap : Map[String, FieldType.Value],
    translationMap : Map[String, String]
  ) = {
    val itemsFuture = originalDataRepo.find(sort = Seq(AscSort("_id")))

    val convertedJsItems = Await.result(itemsFuture, timeout).map { item =>
      val newFieldValues: Seq[(String, JsValue)] = item.fields.filter{case (attribute, value) =>
        attribute == "_id" || fieldTypeMap.get(attribute).get != FieldType.Null
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
              val convertedValue = fieldTypeMap.get(attribute).get match {
                case FieldType.Null => JsNull
                case FieldType.Date => Json.toJson(storeDateFormat.format(toDate(string)))
                case FieldType.Boolean => Json.toJson(toBoolean(string))
                case FieldType.Integer => toJsonNum(string)
                case FieldType.Double => toJsonNum(string)
                case FieldType.Enum => Json.toJson(translationMap.get(string).getOrElse(string))
                case FieldType.String => Json.toJson(translationMap.get(string).getOrElse(string))
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

  protected def fieldTypeMap : Map[String, FieldType.Value] = {
    val fieldsFuture = originalDictionaryRepo.find(sort = Seq(AscSort("name")))

    Await.result(fieldsFuture, timeout).map{
      field => (field.name, field.fieldType)
    }.toMap
  }

  def dateExpectedException(string : String) =
    new IllegalArgumentException(s"String ${string} is expected to be date-convertible but it's not.")

  def booleanExpectedException(string : String) =
    new IllegalArgumentException(s"String ${string} is expected to be boolean-convertible but it's not.")

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