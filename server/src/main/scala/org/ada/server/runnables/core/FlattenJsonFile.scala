package org.ada.server.runnables.core

import java.io.File

import org.ada.server.dataaccess.JsonUtil
import org.incal.core.runnables.InputRunnableExt
import org.incal.core.util.writeStringAsStream
import play.api.libs.json.{JsArray, JsObject, Json}

import scala.io.Source

class FlattenJsonFile extends InputRunnableExt[FlattenJsonFileSpec] {

  private val defaultDelimiter = "_"

  override def run(input: FlattenJsonFileSpec) = {
    val jsonString = Source.fromFile(input.fileName).mkString

    val flattenedJsonString = Json.parse(jsonString).as[JsArray].value.map { json =>
      Json.stringify(JsonUtil.flatten(json.as[JsObject], input.nestedFieldDelimiter.getOrElse(defaultDelimiter)))
    }.mkString(",")

    writeStringAsStream("[" + flattenedJsonString + "]", new File(input.fileName + "-flat"))
  }
}

case class FlattenJsonFileSpec(
  fileName: String,
  nestedFieldDelimiter: Option[String]
)