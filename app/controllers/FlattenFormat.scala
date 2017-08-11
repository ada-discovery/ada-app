package controllers

import play.api.libs.json.{JsObject, _}
import _root_.util.JsonUtil

class FlattenFormat[T](val format: Format[T], delimiter: String = ".") extends Format[T] {

  override def reads(json: JsValue): JsResult[T] = {
    val newJson = json match {
      case jsObject: JsObject => JsonUtil.deflatten(jsObject, delimiter)
      case json => json
    }

    format.reads(newJson)
  }

  override def writes(json: T): JsValue =
    format.writes(json) match {
      case jsObject: JsObject => JsonUtil.flatten(jsObject, delimiter)
      case json => json
    }
}