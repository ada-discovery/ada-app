package controllers

import javax.inject.Inject

import org.apache.commons.lang3.StringEscapeUtils
import models.{Page, FieldType}
import persistence.DictionaryFieldRepo
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{RequestHeader, Action, Call}
import play.twirl.api.Html
import util.WebExportUtil.stringToFile
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.DeNoPaTranSMARTMapping._
import services.TranSMARTService
import views.html
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await

protected abstract class DataSetController(dictionaryRepo: DictionaryFieldRepo)
  extends ReadonlyController[JsObject, BSONObjectID](dictionaryRepo.dataRepo) with ExportableAction[JsObject] {

  dictionaryRepo.initIfNeeded

  @Inject var tranSMARTService: TranSMARTService = _

  // hooks
  protected def showTitle : String

  protected def listTitle : String

  protected def keyField : String

  protected def exportOrderByField : String

  protected def csvFileName : String

  protected def jsonFileName : String

  protected def transSMARTDataFileName : String

  protected def transSMARTMappingFileName : String

  protected def router : DataSetRouter

  // generic show view
  override protected def showView(id : BSONObjectID, item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    html.dataset.show(
      showTitle,
      item,
      router.plainFindCall
    ).asInstanceOf[Html]

  // generic list view
  override def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.dataset.list(
      listTitle,
      currentPage,
      currentOrderBy,
      currentFilter,
      listViewColumns.get,
      router
    ).asInstanceOf[Html]

  def exportRecordsAsCsv(delimiter : String) =
    exportAllToCsv(csvFileName, delimiter, exportOrderByField)

  def exportRecordsAsJson =
    exportAllToJson(jsonFileName, exportOrderByField)

  def getFieldValues(fieldName : String) = {
    for {
      field <- dictionaryRepo.get(fieldName)
      values <- repo.find(None, None, Some(Json.obj(fieldName -> 1)))
    } yield {
      val fieldType = field.get.fieldType
      values.map { item =>
        val jsValue = (item \ fieldName).get
        jsValue match {
          case JsNull => None
          case x : JsString => Some(jsValue.as[String])
          case _ => Some(jsValue.toString)
        }
      }
    }
  }

  def overviewFieldTypes = Action.async { implicit request =>
    dictionaryRepo.find().map{ fields =>
      if (fields.isEmpty)
        throw new IllegalStateException(s"Empty dictionary found. Pls. create one by running 'standalone.InferXXXDictionary' script.")

      val fieldTypeCounts = ArrayBuffer.fill(FieldType.values.size)(0)
      fields.foreach { field =>
        val fieldType = field.fieldType
        fieldTypeCounts(fieldType.id) += 1
      }

      implicit val msg = messagesApi.preferred(request)
      Ok(views.html.dataset.typeOverview("Baseline Type Overview", (FieldType.values, fieldTypeCounts).zipped.toList))
    }
  }

  private def readJsValueTyped(value : JsValue, fieldType : FieldType.Value) {

  }

  /**
   * TranSMART functionality
   */
  def exportTranSMARTDataFile(delimiter : String) = Action { implicit request =>
    val fileContents = getTransSMARTDataAndMappingFiles(transSMARTDataFileName, delimiter, exportOrderByField)
    stringToFile(transSMARTDataFileName)(fileContents._1)
  }

  def exportTranSMARTMappingFile(delimiter : String) = Action { implicit request =>
    val fileContents = getTransSMARTDataAndMappingFiles(transSMARTDataFileName, delimiter, exportOrderByField)
    stringToFile(transSMARTMappingFileName)(fileContents._2)
  }

  protected def getTransSMARTDataAndMappingFiles(dataFilename: String, delimiter: String, orderBy : String) = {
    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", List[(String, String)]())(
      records, dataFilename, keyField, None, fieldCategoryMap, rootCategory, fieldLabelMap)
  }
}