package controllers

import java.util.concurrent.TimeoutException

import util.jsonObjectsToCsv
import scala.concurrent.duration._
import services.TranSMARTService
import services.DeNoPaTranSMARTMapping._
import models.{Category => CCategory, Page}
import org.apache.commons.lang3.StringEscapeUtils
import persistence.AsyncReadonlyRepo
import play.api.Logger
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.mvc._
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Await

abstract class JsObjectReadonlyController(
    repo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    tranSMARTService: TranSMARTService,
    messagesApi: MessagesApi
  ) extends Controller {

  val exportCharset = "UTF-8"
  val timeout = 120000 millis
  val keyField = "Probanden_Nr"

  def listViewColumns : List[String]

  def showView(item: JsObject)(implicit msg: Messages, request: RequestHeader): Html

  def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader): Html

  def csvFileName : String

  def transSMARTDataFileName : String

  def transSMARTMappingFileName : String



  protected def listViewProjection = JsObject(listViewColumns.map(column => (column, Json.toJson(1))))

  def get(id: BSONObjectID) = Action.async { implicit request =>
    repo.get(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    ) { entity =>
      implicit val msg = messagesApi.preferred(request)

      Ok(showView(entity))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the get process")
        InternalServerError(t.getMessage)
    }
  }

  /**
   * Display the paginated list.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param query Filter applied on items
   */
  def find(page: Int, orderBy: String, query: String) = Action.async { implicit request =>
    val limit = 20
    val criteria = if (!query.isEmpty)
      Some(Json.obj("Probanden_Nr" -> Json.obj("$regex" -> (query + ".*"), "$options" -> "i")))
    else
      None

    val sort = if (!orderBy.isEmpty) {
      if (orderBy.startsWith("-"))
        Some(Json.obj(orderBy.substring(1) -> -1))
      else
        Some(Json.obj(orderBy -> 1))
    } else
      None

    val futureItems = repo.find(criteria, sort, Some(listViewProjection), Some(limit), Some(page))
    val futureCount = repo.count(criteria)
    futureItems.zip(futureCount).map({ case (items, count) =>
      implicit val msg = messagesApi.preferred(request)

      Ok(listView(Page(items, page, page * limit, count), orderBy, query))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the list process")
        InternalServerError(t.getMessage)
    }
  }

  def exportRecordsAsCsv(delimiter : String) = exportRecordsAsCsvTo(csvFileName, delimiter)

  def exportTransSMARTDataFile(delimiter : String) = exportTransSMARTMappingFileAsCsvTo(transSMARTDataFileName, delimiter)

  def exportTransSMARTMappingFile(delimiter : String) = exportTransSMARTMappingFileAsCsvTo(transSMARTDataFileName, transSMARTMappingFileName, delimiter)

  protected def exportRecordsAsCsvTo(filename: String, delimiter: String) = Action { implicit request =>
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    val recordsFuture = repo.find(None, Some(Json.obj("Line_Nr" -> 1)), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    val csvString = jsonObjectsToCsv(unescapedDelimiter, "\n", List[(String, String)]())(records)
    val fileContent: Enumerator[Array[Byte]] = Enumerator(csvString.getBytes(exportCharset))

    Result(
      header = ResponseHeader(200, Map(CONTENT_TYPE -> "application/x-download", CONTENT_LENGTH -> csvString.length.toString, CONTENT_DISPOSITION -> s"attachment; filename=${filename}.csv")),
      body = fileContent
    )
  }

  val mmstSumField = "a_CRF_MMST_Summe"
  val mmstCognitiveCategoryField = "a_CRF_MMST_Category"

  protected def exportTransSMARTMappingFileAsCsvTo(dataFilename: String, delimiter: String) = Action { implicit request =>
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)

    val recordsFuture = repo.find(None, Some(Json.obj("Line_Nr" -> 1)), None, None, None)
    val records = Await.result(recordsFuture, timeout)
    val extendedRecords = getExtendedRecords(records)

    val fileContents = tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", List[(String, String)]())(extendedRecords.toList, dataFilename, keyField, None, fieldCategoryMap, rootCategory, fieldLabelMap)

    val fileContent: Enumerator[Array[Byte]] = Enumerator(fileContents._1.getBytes(exportCharset))

    Result(
      header = ResponseHeader(200, Map(CONTENT_TYPE -> "application/x-download", CONTENT_LENGTH -> fileContents._1.length.toString, CONTENT_DISPOSITION -> s"attachment; filename=${dataFilename}")),
      body = fileContent
    )
  }

  protected def exportTransSMARTMappingFileAsCsvTo(dataFilename: String, mappingFilename: String, delimiter: String) = Action { implicit request =>
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)

    val recordsFuture = repo.find(None, Some(Json.obj("Line_Nr" -> 1)), None, None, None)
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