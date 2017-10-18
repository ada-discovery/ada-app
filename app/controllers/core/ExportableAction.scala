package controllers.core

import dataaccess.{AsyncReadonlyRepo, Criterion, FieldType, Sort}
import models.FilterCondition
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import _root_.util.WebExportUtil.{jsonsToCsvFile, jsonsToJsonFile}

import scala.concurrent.Future

trait ExportableAction[E] {

  protected def repoHook: AsyncReadonlyRepo[E, _]

  protected def toSort(string: String): Seq[Sort]

  protected def toCriteria(filter: Seq[FilterCondition]): Future[Seq[Criterion[Any]]]

  def exportToCsv(
    filename: String,
    delimiter: String,
    eol: String,
    charReplacements: Traversable[(String, String)] = Nil)(
    orderBy: Option[String],
    filter: Seq[FilterCondition] = Nil,
    projection: Traversable[String] = Nil,
    fieldNames: Option[Seq[String]] = None,
    nameFieldTypeMap: Map[String, FieldType[_]] = Map()
  )(
    implicit ev: Format[E]
  ) = Action.async { implicit request =>
    for {
      jsons <- getJsons(filter, orderBy, projection)
    } yield {
      val finalJsons =
        if (nameFieldTypeMap.nonEmpty)
          toDisplayJsons(jsons, nameFieldTypeMap)
        else
          jsons

      jsonsToCsvFile(filename, delimiter, eol, charReplacements, fieldNames)(finalJsons)
    }
  }

  def exportToJson(
    filename: String)(
    orderBy: Option[String],
    filter: Seq[FilterCondition] = Nil,
    projection: Traversable[String] = Nil,
    nameFieldTypeMap: Map[String, FieldType[_]] = Map()
  )(
    implicit ev: Format[E]
  ) = Action.async { implicit request =>
    for {
      jsons <- getJsons(filter, orderBy, projection)
    } yield {
      val finalJsons =
        if (nameFieldTypeMap.nonEmpty)
          toDisplayJsons(jsons, nameFieldTypeMap)
        else
          jsons

      jsonsToJsonFile(filename)(finalJsons)
    }
  }

  private def getJsons(
    filter: Seq[FilterCondition],
    orderBy: Option[String],
    projection: Traversable[String] = Nil
  )(implicit ev: Format[E]) =
    for {
      criteria <- toCriteria(filter)
      records <- repoHook.find(
        criteria = criteria,
        sort = orderBy.fold(Seq[Sort]())(toSort),
        projection = projection
      )
    } yield {
      if (!records.isEmpty && records.head.isInstanceOf[JsObject]) {
        // if jsobject no need to convert
        records.asInstanceOf[Traversable[JsObject]]
      } else
        Json.toJson(records).as[Traversable[JsObject]]
    }

  private def toDisplayJsons(
    jsons: Traversable[JsObject],
    nameFieldTypeMap: Map[String, FieldType[_]]
  ) =
    jsons.map { item =>
      val displayJsonFields = item.fields.map { case (fieldName, json) =>
        val displayJson = json match {
          case JsNull => JsNull
          case _ =>
            nameFieldTypeMap.get(fieldName).map { fieldType =>
              JsString(fieldType.jsonToDisplayString(json))
            }.getOrElse(json)
        }

        (fieldName, displayJson)
      }
      JsObject(displayJsonFields)
    }
}