package controllers

import persistence.AsyncReadonlyRepo
import play.api.libs.json.{Json, Format, JsObject}
import util.WebExportUtil.{jsonsToCsvFile, jsonsToJsonFile}
import play.api.mvc._

import scala.concurrent.Await
import scala.concurrent.duration._

protected trait ExportableAction[E] {

  private implicit val jsonFormatable = implicitly[Format[E]]

  protected val timeout = 120000 millis

  protected def csvFileName : String

  protected def repo: AsyncReadonlyRepo[E, _]

  protected def toJsonSort(string : String) : Option[JsObject]

  def exportToCsv(filename: String, delimiter: String, orderBy: String) = Action { implicit request =>
    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    val docs = Json.toJson(records).as[Traversable[JsObject]]

    jsonsToCsvFile(filename, delimiter)(docs)
  }

  def exportToJson(filename: String, delimiter: String, orderBy: String) = Action { implicit request =>
    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    val docs = Json.toJson(records).as[Traversable[JsObject]]

    jsonsToJsonFile(filename, delimiter)(docs)
  }
}