package controllers

import persistence.AsyncReadonlyRepo
import util.WebExportUtil.jsonsToCsvFile
import play.api.libs.json._
import play.api.mvc._
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Await
import scala.concurrent.duration._

protected abstract class JsObjectReadonlyController(repo: AsyncReadonlyRepo[JsObject, BSONObjectID]) extends ReadonlyController[JsObject, BSONObjectID](repo) {

  protected val timeout = 120000 millis

  protected def csvFileName : String

  def exportRecordsAsCsvTo(filename: String, delimiter: String, orderBy: String) = Action { implicit request =>
    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    jsonsToCsvFile(filename, delimiter)(records)
  }
}