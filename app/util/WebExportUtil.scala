package util

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.apache.commons.lang3.StringEscapeUtils
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsObject
import play.api.http.HeaderNames._
import dataaccess.JsonUtil.jsonObjectsToCsv
import play.api.http.HttpEntity
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
  ): Result = {
    val source: Source[ByteString, _] = Source.single(ByteString(content, charset))

    Result(
      header = ResponseHeader(200, Map(CONTENT_DISPOSITION -> s"attachment; filename=${filename}")),
      body = HttpEntity.Streamed(source, None, Some("application/x-download")) // source.via(Compression.gzip) Some(content.length)
    )
  }
}
