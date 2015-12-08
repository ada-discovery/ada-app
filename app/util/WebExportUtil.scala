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
    replacements : Option[List[(String, String)]] = None,
    charset : String = DEFAULT_CHARSET)(
    jsons : Traversable[JsObject]
  ) = {
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    val csvString = jsonObjectsToCsv(unescapedDelimiter, "\n", replacements.getOrElse(List[(String, String)]()))(jsons)
    stringToFile(filename, charset)(csvString)
  }

  def jsonsToJsonFile(
    filename: String,
    charset : String = DEFAULT_CHARSET)(
    jsons : Traversable[JsObject]
  ) = {
    val content = jsons.map(_.toString()).mkString(",\n")
    stringToFile(filename, charset)(content)
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
        CONTENT_LENGTH -> content.length.toString,
        CONTENT_DISPOSITION -> s"attachment; filename=${filename}")
      ),
      body = fileContent
    )
  }
}
