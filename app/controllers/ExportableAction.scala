package controllers

import persistence.{Sort, AsyncReadonlyRepo}
import play.api.libs.json.{Json, Format, JsObject}
import util.WebExportUtil.{jsonsToCsvFile, jsonsToJsonFile}
import play.api.mvc._

import scala.concurrent.Await
import scala.concurrent.duration._

protected trait ExportableAction[E] {

  protected val timeout = 120000 millis

  protected def repoHook: AsyncReadonlyRepo[E, _]

  protected def toSort(string : String) : Option[Seq[Sort]]

  def exportAllToCsv(
    filename: String,
    delimiter: String,
    orderBy: String)(
    implicit ev: Format[E]
  ) = Action { implicit request =>
    jsonsToCsvFile(filename, delimiter)(getJsons(None, orderBy))
  }

  def exportAllToJson(
    filename: String,
    orderBy: String)(
    implicit ev: Format[E]
  ) = Action { implicit request =>
    jsonsToJsonFile(filename)(getJsons(None, orderBy))
  }

  def exportToCsv(
     filename: String,
     delimiter: String,
     criteria : Option[JsObject],
     orderBy: String)(
     implicit ev: Format[E]
  ) = Action { implicit request =>
    jsonsToCsvFile(filename, delimiter)(getJsons(criteria, orderBy))
  }

  def exportToJson(
    filename: String,
    criteria : Option[JsObject],
    orderBy: String)(
    implicit ev: Format[E]
  ) = Action { implicit request =>
    jsonsToJsonFile(filename)(getJsons(criteria, orderBy))
  }

  private def getJsons(criteria : Option[JsObject], orderBy: String)(implicit ev: Format[E]) = {
    val recordsFuture = repoHook.find(criteria, toSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    if (!records.isEmpty && records.head.isInstanceOf[JsObject]) {
      // if jsobject no need to convert
      records.asInstanceOf[Traversable[JsObject]]
    } else
      Json.toJson(records).as[Traversable[JsObject]]
  }
}