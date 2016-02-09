package controllers

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.libs.json.{JsString, JsObject, Json}

object MapJsonFormatter {

  def apply = new Formatter[Map[String, String]] {

    def bind(key: String, data: Map[String, String]) = {
      try {
        data.get(key).map { string =>
          val jsObject = Json.parse(string).as[JsObject]
          val keyValues = jsObject.fields.map{ case (key, value) => (key, value.as[String])}
          Right(keyValues.toMap)
        }.getOrElse(
          Left(List(FormError(key, s"No value found for the key '$key'")))
        )
      } catch {
        case e: Exception => Left(List(FormError(key, e.getMessage)))
      }
    }

    def unbind(key: String, value: Map[String, String]) = {
      val lala = value.map{ case (key, value) => (key, JsString(value))}.toSeq
      Map(key -> Json.stringify(JsObject(lala)))
    }
  }
}
