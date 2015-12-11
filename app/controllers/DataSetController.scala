package controllers

import javax.inject.Inject

import controllers.{ReadonlyController, ExportableAction}
import org.apache.commons.lang3.StringEscapeUtils
import persistence.AsyncReadonlyRepo
import play.api.mvc.Action
import util.WebExportUtil.stringToFile
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.DeNoPaTranSMARTMapping._
import services.TranSMARTService

import scala.concurrent.Await

protected abstract class DataSetController(
    repo: AsyncReadonlyRepo[JsObject, BSONObjectID])
  extends ReadonlyController[JsObject, BSONObjectID](repo) with ExportableAction[JsObject] {

  private val keyField = "Probanden_Nr"
  private val lineNrField = "Line_Nr"
  private val mmstSumField = "a_CRF_MMST_Summe"
  private val mmstCognitiveCategoryField = "a_CRF_MMST_Category"

  @Inject var tranSMARTService: TranSMARTService = _

  protected def csvFileName : String

  protected def jsonFileName : String

  protected def transSMARTDataFileName : String

  protected def transSMARTMappingFileName : String

  def exportRecordsAsCsv(delimiter : String) =
    exportAllToCsv(csvFileName, delimiter, lineNrField)

  def exportRecordsAsJson =
    exportAllToJson(jsonFileName, lineNrField)

  /**
   * TranSMART functionality
   */
  def exportTranSMARTDataFile(delimiter : String) = Action { implicit request =>
    val fileContents = getTransSMARTDataAndMappingFiles(transSMARTDataFileName, delimiter, lineNrField)
    stringToFile(transSMARTDataFileName)(fileContents._1)
  }

  def exportTranSMARTMappingFile(delimiter : String) = Action { implicit request =>
    val fileContents = getTransSMARTDataAndMappingFiles(transSMARTDataFileName, delimiter, lineNrField)
    stringToFile(transSMARTMappingFileName)(fileContents._2)
  }

  private def getTransSMARTDataAndMappingFiles(dataFilename: String, delimiter: String, orderBy : String) = {
    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)
    val extendedRecords = getExtendedRecords(records)

    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", List[(String, String)]())(extendedRecords.toList, dataFilename, keyField, None, fieldCategoryMap, rootCategory, fieldLabelMap)
  }

  // Filter definition
  override protected def toJsonCriteria(string : String) =
    if (!string.isEmpty)
      Some(Json.obj("Probanden_Nr" -> Json.obj("$regex" -> (string + ".*"), "$options" -> "i")))
    else
      None

  // Ad-hoc extension requested by Venkata
  private def getExtendedRecords(records : Traversable[JsObject]) =
    records.map{ record =>
      val mmstSum = (record \ mmstSumField).toOption
      if (mmstSum.isDefined) {
        val category = if (mmstSum.get == JsNull) {
          null
        } else {
          val sum = mmstSum.get.asOpt[Int]
          if (!sum.isDefined)
            null
          else
            sum.get match {
              case x if x <= 9 => "Severe"
              case x if ((x >= 10) && (x <= 18)) => "Moderate"
              case x if ((x >= 19) && (x <= 24)) => "Mild"
              case x if ((x >= 25) && (x <= 26)) => "Sub-Normal"
              case x if ((x >= 27) && (x <= 30)) => "Normal"
            }
        }
        record + (mmstCognitiveCategoryField -> Json.toJson(category))
      } else
        record
  }
}