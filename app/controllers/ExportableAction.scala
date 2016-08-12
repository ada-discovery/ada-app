package controllers

import dataaccess.{Sort, AsyncReadonlyRepo, Criterion}
import play.api.libs.json.{Json, Format, JsObject}
import util.FilterCondition
import util.WebExportUtil.{jsonsToCsvFile, jsonsToJsonFile}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._

protected trait ExportableAction[E] {

  protected def repoHook: AsyncReadonlyRepo[E, _]

  protected def toSort(string: String): Seq[Sort]

  protected def toCriteria(filter: Seq[FilterCondition]): Seq[Criterion[Any]]

  def exportAllToCsv(
    filename: String,
    delimiter: String,
    orderBy: String,
    fieldNames: Option[Seq[String]] = None)(
    implicit ev: Format[E]
  ) = Action.async { implicit request =>
    getJsons(Nil, orderBy).map(
      jsonsToCsvFile(filename, delimiter, fieldNames)(_)
    )
  }

  def exportAllToJson(
    filename: String,
    orderBy: String)(
    implicit ev: Format[E]
  ) = Action.async { implicit request =>
    getJsons(Nil, orderBy).map(
      jsonsToJsonFile(filename)(_)
    )
  }

  def exportToCsv(
     filename: String,
     delimiter: String,
     filter: Seq[FilterCondition],
     orderBy: String,
     fieldNames: Option[Seq[String]] = None)(
     implicit ev: Format[E]
  ) = Action.async { implicit request =>
    getJsons(filter, orderBy).map(
      jsonsToCsvFile(filename, delimiter, fieldNames)(_)
    )
  }

  def exportToJson(
    filename: String,
    filter: Seq[FilterCondition],
    orderBy: String)(
    implicit ev: Format[E]
  ) = Action.async { implicit request =>
    getJsons(filter, orderBy).map(
      jsonsToJsonFile(filename)(_)
    )
  }

  private def getJsons(filter: Seq[FilterCondition], orderBy: String)(implicit ev: Format[E]) =
    for {
      records <- repoHook.find(toCriteria(filter), toSort(orderBy))
    } yield {
      if (!records.isEmpty && records.head.isInstanceOf[JsObject]) {
        // if jsobject no need to convert
        records.asInstanceOf[Traversable[JsObject]]
      } else
        Json.toJson(records).as[Traversable[JsObject]]
    }
}