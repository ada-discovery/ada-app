package util

import org.apache.commons.lang3.StringEscapeUtils
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsObject
import play.api.http.HeaderNames._
import JsonUtil.jsonObjectsToCsv
import play.api.mvc.{ResponseHeader, Result}

object WebExportUtil {

  private val DEFAULT_CHARSET = "UTF-8"

  def jsonsToCsvFile(
    filename: String,
    delimiter: String,
    eol: String,
    charReplacements: Traversable[(String, String)] = Nil,
    fieldNames: Option[Seq[String]] = None,
    charset : String = DEFAULT_CHARSET)(
    jsons : Traversable[JsObject]
  ) = {
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    val unescapedEOL = StringEscapeUtils.unescapeJava(eol)
    val csvString = jsonObjectsToCsv(unescapedDelimiter, unescapedEOL, fieldNames, charReplacements)(jsons)
    stringToFile(filename, charset)(csvString)
  }

  def jsonsToJsonFile(
    filename: String,
    charset : String = DEFAULT_CHARSET)(
    jsons : Traversable[JsObject]
  ) = {
    val content = jsons.map(_.toString).mkString(",\n")
    stringToFile(filename, charset)(s"[$content]")
  }

  def stringToFile(
    filename: String,
    charset : String = DEFAULT_CHARSET)(
    content : String
  ) = {
    val fileContent: Enumerator[Array[Byte]] = Enumerator(content.getBytes(charset))

    Result(
      header = ResponseHeader(200, Map(
        CONTENT_TYPE -> "application/x-download",
        // TODO: an explicit setting of the content length sometimes shrinks the file by a few characters
 //       CONTENT_LENGTH -> content.length.toString,
        CONTENT_DISPOSITION -> s"attachment; filename=${filename}")
      ),
      body = fileContent
    )
  }
}
