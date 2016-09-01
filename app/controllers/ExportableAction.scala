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

  def exportToCsv(
    filename: String,
    delimiter: String,
    eol: String,
    charReplacements: Traversable[(String, String)] = Nil)(
    orderBy: String,
    filter: Seq[FilterCondition] = Nil,
    projection: Traversable[String] = Nil,
    fieldNames: Option[Seq[String]] = None)(
    implicit ev: Format[E]
  ) = Action.async { implicit request =>
    getJsons(filter, orderBy, projection).map(
      jsonsToCsvFile(filename, delimiter, eol, charReplacements, fieldNames)(_)
    )
  }

  def exportToJson(
    filename: String)(
    orderBy: String,
    filter: Seq[FilterCondition] = Nil,
    projection: Traversable[String] = Nil)(
    implicit ev: Format[E]
  ) = Action.async { implicit request =>
    getJsons(filter, orderBy, projection).map(
      jsonsToJsonFile(filename)(_)
    )
  }

  private def getJsons(
    filter: Seq[FilterCondition],
    orderBy: String,
    projection: Traversable[String] = Nil
  )(implicit ev: Format[E]) =
    for {
      records <- repoHook.find(
        criteria = toCriteria(filter),
        sort = toSort(orderBy),
        projection = projection
      )
    } yield {
      if (!records.isEmpty && records.head.isInstanceOf[JsObject]) {
        // if jsobject no need to convert
        records.asInstanceOf[Traversable[JsObject]]
      } else
        Json.toJson(records).as[Traversable[JsObject]]
    }
}