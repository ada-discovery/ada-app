package dataaccess.elastic

import play.api.libs.json._
import collection.mutable.{Map => MMap}

class ElasticIdRenameFormat[T](format: Format[T]) extends  Format[T] {

  private val jsFormat = JsonIdRenameFormat.apply

  override def reads(json: JsValue): JsResult[T] =
    format.compose(jsFormat).reads(json)

  override def writes(o: T): JsValue =
    format.transform(jsFormat).writes(o)
}

private class JsonRenameFormat(
    originalFieldName: String,
    newFieldName: String
  ) extends Format[JsValue] {

  override def reads(json: JsValue): JsResult[JsValue] = {
    val newJson = rename(json, newFieldName, originalFieldName)
    JsSuccess(newJson)
  }

  override def writes(json: JsValue): JsValue =
    rename(json, originalFieldName, newFieldName)

  private def rename(json: JsValue, from: String, to: String): JsValue =
    json match {
      case jsObject: JsObject =>
        (jsObject \ from) match {
          case JsDefined(value) => {
            val newValues = MMap[String, JsValue]()
            newValues.++=(jsObject.value)
            newValues.-=(from)
            newValues.+=((to, value))
            JsObject(newValues)
          }
          case _ =>
            jsObject
      }

      case _ => json
    }
}

object JsonIdRenameFormat {
  val originalIdName = "_id"
  val newIdName = "__id"

  def apply: Format[JsValue] = new JsonRenameFormat(originalIdName, newIdName)
}