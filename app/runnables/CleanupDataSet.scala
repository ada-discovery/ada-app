package runnables

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date

import javax.inject.Inject

import dataaccess._
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
    fieldTypeMap : Map[String, FieldTypeId.Value],
    translationMap : Map[String, String]
  ) = {
    val itemsFuture = originalDataRepo.find(sort = Seq(AscSort("_id")))

    val convertedJsItems = Await.result(itemsFuture, timeout).map { item =>
      val newFieldValues: Seq[(String, JsValue)] = item.fields.filter{case (attribute, value) =>
        attribute == "_id" || fieldTypeMap.get(attribute).get != FieldTypeId.Null
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
                case FieldTypeId.Null => JsNull
                case FieldTypeId.Date => Json.toJson(storeDateFormat.format(ConversionUtil.toDate(dateFormats)(string)))
                case FieldTypeId.Boolean => Json.toJson(ConversionUtil.toBoolean(textBooleanMap ++ numBooleanMap)(string))
                case FieldTypeId.Integer => toJsonNum(string)
                case FieldTypeId.Double => toJsonNum(string)
                case FieldTypeId.Enum => Json.toJson(translationMap.get(string).getOrElse(string))
                case FieldTypeId.String => Json.toJson(translationMap.get(string).getOrElse(string))
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
    convertedJsItems.filter(item => item.fields.exists{ case (attribute, value) =>
      attribute != "Line_Nr" && attribute != "orig_id" && attribute != "_id" && value != JsNull
    })
  }

  private def toJsonNum(string : String) = {
    try {
      Json.toJson(string.toLong)
    } catch {
      case t: NumberFormatException => Json.toJson(string.toDouble)
    }
  }

  protected def fieldTypeMap : Map[String, FieldTypeId.Value] = {
    val fieldsFuture = originalDictionaryRepo.find(sort = Seq(AscSort("name")))

    Await.result(fieldsFuture, timeout).map{
      field => (field.name, field.fieldType)
    }.toMap
  }
}