package controllers.denopa

import controllers.JsObjectReadonlyController
import org.apache.commons.lang3.StringEscapeUtils
import persistence.AsyncReadonlyRepo
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
import services.DeNoPaTranSMARTMapping._
import services.TranSMARTService

import scala.concurrent.Await

abstract class DeNoPaController(
    repo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    tranSMARTService: TranSMARTService
  ) extends JsObjectReadonlyController(repo) {

  private val keyField = "Probanden_Nr"

  protected def transSMARTDataFileName : String

  protected def transSMARTMappingFileName : String

  override protected def toJsonCriteria(string : String) =
    if (!string.isEmpty)
      Some(Json.obj("Probanden_Nr" -> Json.obj("$regex" -> (string + ".*"), "$options" -> "i")))
    else
      None

  def exportRecordsAsCsv(delimiter : String) = exportRecordsAsCsvTo(csvFileName, delimiter, "Line_Nr")

  def exportTransSMARTDataFile(delimiter : String) = exportTransSMARTMappingFileAsCsvTo(transSMARTDataFileName, delimiter, "Line_Nr")

  def exportTransSMARTMappingFile(delimiter : String) = exportTransSMARTMappingFileAsCsvTo(transSMARTDataFileName, transSMARTMappingFileName, delimiter, "Line_Nr")

  val mmstSumField = "a_CRF_MMST_Summe"
  val mmstCognitiveCategoryField = "a_CRF_MMST_Category"

  protected def exportTransSMARTMappingFileAsCsvTo(dataFilename: String, delimiter: String, orderBy : String) = Action { implicit request =>
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)

    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)
    val extendedRecords = getExtendedRecords(records)

    val fileContents = tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", List[(String, String)]())(extendedRecords.toList, dataFilename, keyField, None, fieldCategoryMap, rootCategory, fieldLabelMap)

    val fileContent: Enumerator[Array[Byte]] = Enumerator(fileContents._1.getBytes(exportCharset))

    Result(
      header = ResponseHeader(200, Map(CONTENT_TYPE -> "application/x-download", CONTENT_LENGTH -> fileContents._1.length.toString, CONTENT_DISPOSITION -> s"attachment; filename=${dataFilename}")),
      body = fileContent
    )
  }

  protected def exportTransSMARTMappingFileAsCsvTo(dataFilename: String, mappingFilename: String, delimiter: String, orderBy : String) = Action { implicit request =>
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)

    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)
    val extendedRecords = getExtendedRecords(records)

    val fileContents = tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", List[(String, String)]())(extendedRecords.toList, dataFilename, keyField, None, fieldCategoryMap, rootCategory, fieldLabelMap)

    val fileContent: Enumerator[Array[Byte]] = Enumerator(fileContents._2.getBytes(exportCharset))

    Result(
      header = ResponseHeader(200, Map(CONTENT_TYPE -> "application/x-download", CONTENT_LENGTH -> fileContents._2.length.toString, CONTENT_DISPOSITION -> s"attachment; filename=${mappingFilename}")),
      body = fileContent
    )
  }

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