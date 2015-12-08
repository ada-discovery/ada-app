package controllers

import persistence.AsyncReadonlyRepo
import play.api.libs.json.{Json, Format, JsObject}
import util.WebExportUtil.{jsonsToCsvFile, jsonsToJsonFile}
import play.api.mvc._

import scala.concurrent.Await
import scala.concurrent.duration._

protected trait ExportableAction[E] {

  protected val timeout = 120000 millis

  protected def repoHook: AsyncReadonlyRepo[E, _]

  protected def toJsonSort(string : String) : Option[JsObject]

  def exportAllToCsv(
    filename: String,
    delimiter: String,
    orderBy: String)(
    implicit ev: Format[E]
  ) = Action { implicit request =>
    jsonsToCsvFile(filename, delimiter)(getJsons(orderBy))
  }

  def exportAllToJson(
    filename: String,
    orderBy: String)(
    implicit ev: Format[E]
  ) = Action { implicit request =>
    jsonsToJsonFile(filename)(getJsons(orderBy))
  }

  private def getJsons(orderBy: String)(implicit ev: Format[E]) = {
    val recordsFuture = repoHook.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    if (!records.isEmpty && records.head.isInstanceOf[JsObject]) {
      // if jsobject no need to convert
      records.asInstanceOf[Traversable[JsObject]]
    } else
      Json.toJson(records).as[Traversable[JsObject]]
  }
}