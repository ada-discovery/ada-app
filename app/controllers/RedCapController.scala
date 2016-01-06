package controllers

import org.apache.commons.lang3.StringEscapeUtils

import scala.concurrent.duration._
import javax.inject.Inject

import models.Page
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{Json, JsObject, JsString, JsNull}
import services.{TranSMARTService, RedCapService}
import views.html
import play.api.mvc.{ResponseHeader, Action, Controller, Result}
import collection.mutable.{Map => MMap}
import models.Category
import util.JsonUtil.jsonObjectsToCsv

import scala.concurrent.Await

class RedCapController @Inject() (
    redCapService: RedCapService,
    tranSMARTService: TranSMARTService,
    messagesApi: MessagesApi
  ) extends Controller {

  private val limit = 20
  private val timeout = 120000 millis
  private val exportCharset = "UTF-8"

  private val csvFileName = "luxpark-redcap_records.csv"
  private val tranSMARTDataFileName = "luxpark-redcap_data_file"
  private val tranSMARTMappingFileName = "luxpark-redcap_mapping_file"
  private val genderField = "cdisc_dm_sex"
  private val deathField = "dm_death"
  private val statusField = "cdisc_sc_sctestcd_maritstat"

  private val replacements = List(("\r", " "), ("\n", " "))
  private val keyField = "cdisc_dm_usubjd"
  private val visitField = Some("redcap_event_name")


  def index = Action { Redirect(routes.RedCapController.listFieldNames()) }

  def listRecords(page: Int, orderBy: String, filter: String) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    redCapService.listRecords(page, orderBy, filter, keyField).map( items =>
      Ok(html.redcap.listRecords(Page(items.drop(page * limit).take(limit), page, page * limit, items.size), orderBy, filter))
    )
  }

  def listMetadatas(page: Int, orderBy: String, filter: String) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    redCapService.listMetadatas(page, orderBy, filter).map( items =>
      Ok(html.redcap.listMetadatas(Page(items.drop(page * limit).take(limit), page, page * limit, items.size), orderBy, filter))
    )
  }

  def listFieldNames(page: Int, orderBy: String, filter: String) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    redCapService.listFieldNames(page, orderBy, filter).map( items =>
      Ok(html.redcap.listFieldNames(Page(items.drop(page * limit).take(limit), page, page * limit, items.size), orderBy, filter))
    )
  }

  def showRecord(id: String) = Action.async { implicit request =>
    redCapService.getRecord(id).map { foundItems =>
      if (foundItems.isEmpty) {
        NotFound(s"Entity #$id not found")
      } else {
        implicit val msg = messagesApi.preferred(request)
        Ok(html.redcap.showRecord(foundItems.head))
      }
    }
  }

  def showMetadata(id: String) = Action.async { implicit request =>
    redCapService.getMetadata(id).map { foundItems =>
      if (foundItems.isEmpty) {
        NotFound(s"Entity #$id not found")
      } else {
        implicit val msg = messagesApi.preferred(request)
        Ok(html.redcap.showMetadata(foundItems.head))
      }
    }
  }

  def showFieldName(id: String) = Action.async { implicit request =>
    redCapService.getFieldName(id).map { foundItems =>
      if (foundItems.isEmpty) {
        NotFound(s"Entity #$id not found")
      } else {
        implicit val msg = messagesApi.preferred(request)
        Ok(html.redcap.showFieldName(foundItems.head))
      }
    }
  }

  def overview = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

      redCapService.listRecords(0, keyField, "", keyField).map { items =>

      val genderValueCounts = createValueCountMap(items, genderField)
      val deathValueCounts = createValueCountMap(items, deathField)
      val statusValueCounts = createValueCountMap(items, statusField)

      Ok(html.redcap.overviewRecords("LuxPark REDCap Overview", genderValueCounts, deathValueCounts, statusValueCounts))
    }
  }

  private def createValueCountMap(items : Iterable[JsObject], fieldName : String) = {
    val countMap = MMap[String, Int]()
    items.map{item =>
      val rawWalue = item.fields.find(_._1 == fieldName).get._2
      val stringValue = if (rawWalue == JsNull)
        null
      else
        rawWalue.as[String]
      val count = countMap.getOrElse(stringValue, 0)
      countMap.update(stringValue, count + 1)
    }
    countMap.toSeq.sortBy(_._2)
  }


  def exportRecordsAsCsv(delimiter : String) = Action { implicit request =>
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)

    val recordsFuture = redCapService.listRecords(0, keyField, "")
    val records = Await.result(recordsFuture, timeout)

    val content = jsonObjectsToCsv(unescapedDelimiter, "\n", replacements)(records)

    val fileContent: Enumerator[Array[Byte]] = Enumerator(content.getBytes(exportCharset))

    Result(
      header = ResponseHeader(200, Map(CONTENT_TYPE->"application/x-download", CONTENT_LENGTH -> content.length.toString, CONTENT_DISPOSITION->s"attachment; filename=${csvFileName}")),
      body = fileContent
    )
  }

  def exportTranSMARTDataFile(delimiter: String) = Action { implicit request =>
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)

    val recordsFuture = redCapService.listRecords(0, keyField, "")
    val records = Await.result(recordsFuture, timeout)

    val metadataFuture = redCapService.listMetadatas(0, "field_name", "")
    val metadatas = Await.result(metadataFuture, timeout)

    // categories
    val rootCategory = new Category("")
    val categories = metadatas.map{ metadata =>
      (metadata \ "form_name").as[String]
    }.toSet.map { name : String => new Category(name) }.toList
    rootCategory.addChildren(categories)
    val nameCategoryMap = categories.map(category => (category.name, category)).toMap

    // field category map
    val fieldCategoryMap = metadatas.map{metadata =>
      val field = (metadata \ "field_name").as[String]
      val categoryName = (metadata \ "form_name").as[String]
      (field, nameCategoryMap.get(categoryName).get)
    }.toMap

    // field label map
    val fieldLabelMap = metadatas.map{metadata =>
      val field = (metadata \ "field_name").as[String]
      val label = (metadata \ "field_label").as[String]
      (field, label)
    }.toMap

    val fileContents = tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", replacements)(records.toList, tranSMARTDataFileName, keyField, visitField, fieldCategoryMap, rootCategory, fieldLabelMap)

    val fileContent: Enumerator[Array[Byte]] = Enumerator(fileContents._1.getBytes(exportCharset))

    Result(
      header = ResponseHeader(200, Map(CONTENT_TYPE -> "application/x-download", CONTENT_LENGTH -> fileContents._1.length.toString, CONTENT_DISPOSITION -> s"attachment; filename=${tranSMARTDataFileName}")),
      body = fileContent
    )
  }

  def exportTranSMARTMappingFile(delimiter: String) = Action { implicit request =>
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)

    val recordsFuture = redCapService.listRecords(0, keyField, "")
    val records = Await.result(recordsFuture, timeout)

    val metadataFuture = redCapService.listMetadatas(0, "field_name", "")
    val metadatas = Await.result(metadataFuture, timeout)

    // categories
    val rootCategory = new Category("")
    val categories = metadatas.map{ metadata =>
      (metadata \ "form_name").as[String]
    }.toSet.map { name : String => new Category(name) }.toList

    rootCategory.addChildren(categories)
    val nameCategoryMap = categories.map(category => (category.name, category)).toMap

    // field category map
    val fieldCategoryMap = metadatas.map{ metadata =>
      val field = (metadata \ "field_name").as[String]
      val categoryName = (metadata \ "form_name").as[String]
      (field, nameCategoryMap.get(categoryName).get)
    }.toMap

    // field label map
    val fieldLabelMap = metadatas.map{metadata =>
      val field = (metadata \ "field_name").as[String]
      val label = (metadata \ "field_label").as[String]
      (field, label)
    }.toMap

    val fileContents = tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", replacements)(records.toList, tranSMARTDataFileName, keyField, visitField, fieldCategoryMap, rootCategory, fieldLabelMap)

    val fileContent: Enumerator[Array[Byte]] = Enumerator(fileContents._2.getBytes(exportCharset))

    // TODO
    // CONTENT_LENGTH -> fileContents._2.length.toString,

    Result(
      header = ResponseHeader(200, Map(CONTENT_TYPE -> "application/x-download", CONTENT_DISPOSITION -> s"attachment; filename=${tranSMARTMappingFileName}")),
      body = fileContent
    )
  }
}