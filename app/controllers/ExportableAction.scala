package controllers

import persistence.{Sort, AsyncReadonlyRepo}
import play.api.libs.json.{Json, Format, JsObject}
import util.WebExportUtil.{jsonsToCsvFile, jsonsToJsonFile}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._

protected trait ExportableAction[E] {

  protected def repoHook: AsyncReadonlyRepo[E, _]

  protected def toSort(string : String): Seq[Sort]

  def exportAllToCsv(
    filename: String,
    delimiter: String,
    orderBy: String)(
    implicit ev: Format[E]
  ) = Action.async { implicit request =>
    getJsons(None, orderBy).map(
      jsonsToCsvFile(filename, delimiter)(_)
    )
  }

  def exportAllToJson(
    filename: String,
    orderBy: String)(
    implicit ev: Format[E]
  ) = Action.async { implicit request =>
    getJsons(None, orderBy).map(
      jsonsToJsonFile(filename)(_)
    )
  }

  def exportToCsv(
     filename: String,
     delimiter: String,
     criteria : Option[JsObject],
     orderBy: String)(
     implicit ev: Format[E]
  ) = Action.async { implicit request =>
    getJsons(criteria, orderBy).map(
      jsonsToCsvFile(filename, delimiter)(_)
    )
  }

  def exportToJson(
    filename: String,
    criteria : Option[JsObject],
    orderBy: String)(
    implicit ev: Format[E]
  ) = Action.async { implicit request =>
    getJsons(criteria, orderBy).map(
      jsonsToJsonFile(filename)(_)
    )
  }

  private def getJsons(criteria : Option[JsObject], orderBy: String)(implicit ev: Format[E]) =
    for {
      records <- repoHook.find(criteria, toSort(orderBy))
    } yield {
      if (!records.isEmpty && records.head.isInstanceOf[JsObject]) {
        // if jsobject no need to convert
        records.asInstanceOf[Traversable[JsObject]]
      } else
        Json.toJson(records).as[Traversable[JsObject]]
    }
}