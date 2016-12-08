package controllers

import be.objectify.deadbolt.scala.DeadboltActions
import models._
import org.apache.commons.lang3.StringEscapeUtils
import play.api.Configuration

import scala.concurrent.duration._
import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{Json, JsObject, JsString, JsNull}
import services.{RedCapServiceFactory, TranSMARTService, RedCapService}
import services.RedCapServiceFactory.defaultRedCapService
import views.html
import play.api.mvc.{ResponseHeader, Action, Controller, Result}
import collection.mutable.{Map => MMap}
import util.JsonUtil.jsonObjectsToCsv
import util.SecurityUtil.restrictAdmin

import scala.concurrent.Await

class RedCapController @Inject() (
    redCapServiceFactory: RedCapServiceFactory,
    tranSMARTService: TranSMARTService,
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi,
    configuration: Configuration
  ) extends Controller {

  private val redCapService = defaultRedCapService(redCapServiceFactory, configuration)
  private val limit = 20
  private val timeout = 120000 millis
  private val exportCharset = "UTF-8"

  private val csvFileName = "luxpark-redcap_records.csv"
  private val tranSMARTDataFileName = "luxpark-redcap_data_file"
  private val tranSMARTMappingFileName = "luxpark-redcap_mapping_file"

  private val fieldsOfInterest = List(("Gender", "cdisc_dm_sex"), ("Survival", "dm_death"), ("Status", "cdisc_sc_sctestcd_maritstat"))

  private val replacements = List(("\r", " "), ("\n", " "))
  private val keyField = "cdisc_dm_usubjd"
  private val visitField = Some("redcap_event_name")


  def index = restrictAdmin(deadbolt) {
    Action { Redirect(routes.RedCapController.listExportFields()) }
  }

  def listRecords(page: Int, orderBy: String, f: String, filter: Seq[FilterCondition]) = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      implicit val msg = messagesApi.preferred(request)

      redCapService.listRecords(orderBy, f).map { items =>
        val newPage = Page(items.drop(page * limit).take(limit), page, page * limit, items.size, orderBy, Some(new models.Filter(filter)))
        Ok(html.redcap.listRecords(newPage, filter))
      }
    }
  }

  def listMetadatas(page: Int, orderBy: String, filter: String) = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      implicit val msg = messagesApi.preferred(request)

      redCapService.listMetadatas(orderBy, filter).map( items =>
        Ok(html.redcap.listMetadatas(Page(items.drop(page * limit).take(limit), page, page * limit, items.size, orderBy, None)))
      )
    }
  }

  def listExportFields(page: Int, orderBy: String, filter: String) = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      implicit val msg = messagesApi.preferred(request)

      redCapService.listExportFields(orderBy, filter).map(items =>
        Ok(html.redcap.listFieldNames(Page(items.drop(page * limit).take(limit), page, page * limit, items.size, orderBy, None)))
      )
    }
  }

  def showRecord(id: String) = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      redCapService.getRecord(id).map { foundItems =>
        if (foundItems.isEmpty) {
          NotFound(s"Entity #$id not found")
        } else {
          implicit val msg = messagesApi.preferred(request)
          Ok(html.redcap.showRecord(foundItems.head))
        }
      }
    }
  }

  def showMetadata(id: String) = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      redCapService.getMetadata(id).map { foundItems =>
        if (foundItems.isEmpty) {
          NotFound(s"Entity #$id not found")
        } else {
          implicit val msg = messagesApi.preferred(request)
          Ok(html.redcap.showMetadata(foundItems.head))
        }
      }
    }
  }

  def showExportField(id: String) = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      redCapService.getExportField(id).map { foundItems =>
        if (foundItems.isEmpty) {
          NotFound(s"Entity #$id not found")
        } else {
          implicit val msg = messagesApi.preferred(request)
          Ok(html.redcap.showFieldName(foundItems.head))
        }
      }
    }
  }

  def overview = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      implicit val msg = messagesApi.preferred(request)

      redCapService.listRecords(keyField, "").map { items =>

        val valueCounts = fieldsOfInterest.map{ case(name, key) =>
          (name, createValueCountMap(items, key))
        }
        Ok(html.redcap.overviewRecords("LuxPark REDCap Overview", valueCounts))
      }
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


  def exportRecordsAsCsv(delimiter : String) = restrictAdmin(deadbolt) {
    Action { implicit request =>
      val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)

      val recordsFuture = redCapService.listRecords(keyField, "")
      val records = Await.result(recordsFuture, timeout)

      val content = jsonObjectsToCsv(unescapedDelimiter, "\n", None, replacements)(records)

      val fileContent: Enumerator[Array[Byte]] = Enumerator(content.getBytes(exportCharset))

      Result(
        header = ResponseHeader(200, Map(CONTENT_TYPE->"application/x-download", CONTENT_LENGTH -> content.length.toString, CONTENT_DISPOSITION->s"attachment; filename=${csvFileName}")),
        body = fileContent
      )
    }
  }

  def exportTranSMARTDataFile(delimiter: String) = restrictAdmin(deadbolt) {
    Action { implicit request =>
      val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)

      val recordsFuture = redCapService.listRecords(keyField, "")
      val records = Await.result(recordsFuture, timeout)

      val metadataFuture = redCapService.listMetadatas("field_name", "")
      val metadatas = Await.result(metadataFuture, timeout)

      // categories
      val rootCategory = new Category("")
      val categories = metadatas.map(_.form_name).toSet.map { formName : String =>
        new Category(formName)
      }.toList

      rootCategory.setChildren(categories)
      val nameCategoryMap = categories.map(category => (category.name, category)).toMap

      // field category map
      val fieldCategoryMap = metadatas.map{metadata =>
        (metadata.field_name, nameCategoryMap.get(metadata.form_name).get)
      }.toMap

      // fields
      val fields = metadatas.map{metadata =>
        Field(metadata.field_name, Some(metadata.field_label), FieldTypeId.String)
      }

      val fileContents = tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", replacements)(records.toList, tranSMARTDataFileName, keyField, visitField, fieldCategoryMap, rootCategory, fields)

      val fileContent: Enumerator[Array[Byte]] = Enumerator(fileContents._1.getBytes(exportCharset))

      Result(
        header = ResponseHeader(200, Map(CONTENT_TYPE -> "application/x-download", CONTENT_LENGTH -> fileContents._1.length.toString, CONTENT_DISPOSITION -> s"attachment; filename=${tranSMARTDataFileName}")),
        body = fileContent
      )
    }
  }

  def exportTranSMARTMappingFile(delimiter: String) = restrictAdmin(deadbolt) {
    Action { implicit request =>
      val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)

      val recordsFuture = redCapService.listRecords(keyField, "")
      val records = Await.result(recordsFuture, timeout)

      val metadataFuture = redCapService.listMetadatas("field_name", "")
      val metadatas = Await.result(metadataFuture, timeout)

      // categories
      val rootCategory = new Category("")
      val categories = metadatas.map(_.form_name).toSet.map { formName : String =>
        new Category(formName)
      }.toList

      rootCategory.setChildren(categories)
      val nameCategoryMap = categories.map(category => (category.name, category)).toMap

      // field category map
      val fieldCategoryMap = metadatas.map{metadata =>
        (metadata.field_name, nameCategoryMap.get(metadata.form_name).get)
      }.toMap

      // fields
      val fields = metadatas.map{metadata =>
        Field(metadata.field_name, Some(metadata.field_label), FieldTypeId.String)
      }

      val fileContents = tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", replacements)(
        records.toList, tranSMARTDataFileName, keyField, visitField, fieldCategoryMap, rootCategory, fields)

      val fileContent: Enumerator[Array[Byte]] = Enumerator(fileContents._2.getBytes(exportCharset))

      // TODO
      // CONTENT_LENGTH -> fileContents._2.length.toString,

      Result(
        header = ResponseHeader(200, Map(CONTENT_TYPE -> "application/x-download", CONTENT_DISPOSITION -> s"attachment; filename=${tranSMARTMappingFileName}")),
        body = fileContent
      )
    }
  }
}