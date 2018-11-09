package controllers.core

import field.FieldType
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import _root_.util.WebExportUtil._
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.{AsyncReadonlyRepo, Criterion, Sort}
import org.incal.play.controllers.ReadonlyControllerImpl

import scala.concurrent.Future

trait ExportableAction[E] {

  this: ReadonlyControllerImpl[E, _] =>

  def exportToCsv(
    filename: String,
    delimiter: String = ",",
    eol: String = "\n",
    replacements: Traversable[(String, String)] = Nil)(
    fieldNames: Traversable[String],
    orderBy: Option[String] = None,
    filter: Seq[FilterCondition] = Nil,
    nameFieldTypeMap: Map[String, FieldType[_]] = Map()
  ) = Action.async { implicit request =>
    for {
      jsonStream <- getJsonsStream(filter, orderBy, fieldNames)
    } yield {
      val finalJsonStream =
        if (nameFieldTypeMap.nonEmpty)
          toDisplayJsonsStream(jsonStream, nameFieldTypeMap)
        else
          jsonStream

      jsonStreamToCsvFile(finalJsonStream, fieldNames, filename, delimiter, eol, replacements)
    }
  }

  def exportToJson(
    filename: String)(
    orderBy: Option[String],
    filter: Seq[FilterCondition] = Nil,
    fieldNames: Traversable[String] = Nil,
    nameFieldTypeMap: Map[String, FieldType[_]] = Map()
  )(
    implicit ev: Format[E]
  ) = Action.async { implicit request =>
    for {
      jsonStream <- getJsonsStream(filter, orderBy, fieldNames)
    } yield {
      val finalJsonStream =
        if (nameFieldTypeMap.nonEmpty)
          toDisplayJsonsStream(jsonStream, nameFieldTypeMap)
        else
          jsonStream

      jsonStreamToJsonFile(finalJsonStream, filename)
    }
  }

  private def getJsons(
    filter: Seq[FilterCondition],
    orderBy: Option[String],
    projection: Traversable[String] = Nil
  ): Future[Traversable[JsObject]] =
    for {
      criteria <- toCriteria(filter)

      records <- repo.find(
        criteria = criteria,
        sort = orderBy.fold(Seq[Sort]())(toSort),
        projection = projection
      )
    } yield {
      if (!records.isEmpty && records.head.isInstanceOf[JsObject]) {
        // if jsobject no need to convert
        records.asInstanceOf[Traversable[JsObject]]
      } else
        records.map(item => toJson(item).as[JsObject])
    }

  private def getJsonsStream(
    filter: Seq[FilterCondition],
    orderBy: Option[String],
    projection: Traversable[String] = Nil
  ): Future[Source[JsObject, _]] =
    for {
      criteria <- toCriteria(filter)

      recordsSource <- repo.findAsStream(
        criteria = criteria,
        sort = orderBy.fold(Seq[Sort]())(toSort),
        projection = projection
      )
    } yield
      recordsSource.map(item => toJson(item).as[JsObject])

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

  private def toDisplayJsonsStream(
    source: Source[JsObject, _],
    nameFieldTypeMap: Map[String, FieldType[_]]
  ): Source[JsObject, _] =
    source.map { item =>
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