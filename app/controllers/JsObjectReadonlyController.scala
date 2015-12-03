package controllers

import org.apache.commons.lang3.StringEscapeUtils
import persistence.AsyncReadonlyRepo
import play.api.libs.iteratee.Enumerator
import util.jsonObjectsToCsv
import play.api.libs.json._
import play.api.mvc._
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Await
import scala.concurrent.duration._

protected abstract class JsObjectReadonlyController(repo: AsyncReadonlyRepo[JsObject, BSONObjectID]) extends ReadonlyController[JsObject, BSONObjectID](repo) {

  protected val exportCharset = "UTF-8"
  protected val timeout = 120000 millis

  protected def csvFileName : String

  def exportRecordsAsCsvTo(filename: String, delimiter: String, orderBy: String) = Action { implicit request =>
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    val csvString = jsonObjectsToCsv(unescapedDelimiter, "\n", List[(String, String)]())(records)
    val fileContent: Enumerator[Array[Byte]] = Enumerator(csvString.getBytes(exportCharset))

    Result(
      header = ResponseHeader(200, Map(
        CONTENT_TYPE -> "application/x-download",
        CONTENT_LENGTH -> csvString.length.toString,
        CONTENT_DISPOSITION -> s"attachment; filename=${filename}.csv")
      ),
      body = fileContent
    )
  }
}