package controllers

import org.apache.commons.lang3.StringEscapeUtils

import scala.concurrent.duration._
import javax.inject.Inject

import models.Page
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsString, JsNull}
import services.RedCapService
import views.html
import play.api.mvc.{ResponseHeader, Action, Controller, Result}
import collection.mutable.{Map => MMap}

import scala.concurrent.Await

class RedCapController @Inject() (
    redCapService: RedCapService,
    messagesApi: MessagesApi
  ) extends Controller {

  val limit = 20
  val timeout = 120000 millis
  val exportCharset = "UTF-8"

  def index = Action { Redirect(routes.RedCapController.listFieldNames()) }

  def listRecords(page: Int, orderBy: String, filter: String) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    redCapService.listRecords(page, orderBy, filter).map( items =>
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

  val genderField = "Cdisc_dm_sex"
  val deathField = "Dm_death"
  val statusField = "Cdisc_sc_sctestcd_maritstat"

//  def overviewRecords = Action.async { implicit request =>
//    implicit val msg = messagesApi.preferred(request)
//
//    redCapService.listRecords(0, "", "").map { items =>
//      val genderMap = MMap[String, Int]()
//      items.map{item =>
//        val gender = item.fields.find(_._1 == genderField).get._2
////        genderMap.getOrElse(gender)
//      }
//      Ok(html.redcap.listRecords(Page(items.drop(page * limit).take(limit), page, page * limit, items.size), orderBy, filter))
//    }
//  }

  def exportRecordsAsCsv(delimiter : String) = Action { implicit request =>
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    val sb = new StringBuilder(10000)
    val recordsFuture = redCapService.listRecords(0, "cdisc_dm_usubjd", "")
    val records = Await.result(recordsFuture, timeout)
    if (!records.isEmpty) {
      val header = records(0).fields.map(_._1.replaceAll("\r", " ").replaceAll("\n", " ")).mkString(unescapedDelimiter)
      sb.append(header + "\n")

      records.foreach { record =>
        val row = record.fields.map { case (attributeName, value) =>
          value match {
            case JsNull => ""
            case _: JsString => value.as[String].replaceAll("\r", " ").replaceAll("\n", " ")
            case _ => value.toString()
          }
        }.mkString(unescapedDelimiter)
        sb.append(row + "\n")
      }
    }

    val fileContent: Enumerator[Array[Byte]] = Enumerator(sb.toString.getBytes(exportCharset))

    Result(
      header = ResponseHeader(200, Map(CONTENT_TYPE->"application/x-download", CONTENT_LENGTH -> sb.length.toString, CONTENT_DISPOSITION->"attachment; filename=luxpark-redcap-records.csv")),
      body = fileContent
    )
//    Ok.sendFile(new File("path to file/abc.csv"), inline=true).withHeaders(CACHE_CONTROL->"max-age=3600",CONTENT_DISPOSITION->"attachment; filename=abc.csv", CONTENT_TYPE->"application/x-download");
  }
}