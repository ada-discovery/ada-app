package controllers

import javax.inject.Inject

import org.apache.commons.lang3.StringEscapeUtils
import persistence.{DictionaryRepo, AsyncReadonlyRepo}
import play.api.mvc.Action
import util.WebExportUtil.stringToFile
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.DeNoPaTranSMARTMapping._
import services.TranSMARTService

import scala.concurrent.Await

protected abstract class DataSetController(dictionary: DictionaryRepo)
  extends ReadonlyController[JsObject, BSONObjectID](dictionary.dataRepo) with ExportableAction[JsObject] {

  @Inject var tranSMARTService: TranSMARTService = _

  protected def keyField : String

  protected def exportOrderByField : String

  protected def csvFileName : String

  protected def jsonFileName : String

  protected def transSMARTDataFileName : String

  protected def transSMARTMappingFileName : String

  def exportRecordsAsCsv(delimiter : String) =
    exportAllToCsv(csvFileName, delimiter, exportOrderByField)

  def exportRecordsAsJson =
    exportAllToJson(jsonFileName, exportOrderByField)

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

  private def getTransSMARTDataAndMappingFiles(dataFilename: String, delimiter: String, orderBy : String) = {
    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", List[(String, String)]())(
      records, dataFilename, keyField, None, fieldCategoryMap, rootCategory, fieldLabelMap)
  }
}