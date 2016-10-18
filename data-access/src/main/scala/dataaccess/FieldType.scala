package dataaccess

import play.api.libs.json._

trait FieldType[T] {
  val spec: FieldTypeSpec
  val valueClass: Class[_]

  protected val nullAliases: Set[String]
  protected def parseWoNull(text: String): T

  def parse(text: String): Option[T] =
    if (text == null)
      None
    else if (nullAliases.contains(text.trim.toLowerCase))
      None
    else
      Some(parseWoNull(text))

  def toJson(value: T): JsValue
  def parseToJson(text: String): JsValue =
    parse(text).fold(JsNull: JsValue)
      {value => toJson(value)}
}

private abstract class FormatFieldType[T: Format] extends FieldType[T] {
  def toJson(value: T): JsValue = Json.toJson(value)
}