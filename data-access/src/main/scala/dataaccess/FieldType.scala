package dataaccess

import models.FieldTypeSpec
import play.api.libs.json._

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

trait FieldType[T] {

  val spec: FieldTypeSpec
  val valueClass: Class[_]
  val classTag: Option[ClassTag[T]]

  protected[dataaccess] val nullAliases: Set[String]

  def displayStringToValue(text: String): Option[T] =
    if (text == null)
      None
    else {
      val trimmedText = text.trim
      if (nullAliases.contains(trimmedText.toLowerCase))
        None
      else
        Some(displayStringToValueWoNull(trimmedText))
    }

  protected def displayStringToValueWoNull(text: String): T

  def valueStringToValue(text: String): Option[T] =
    if (text == null)
      None
    else {
      val trimmedText = text.trim
      if (trimmedText.isEmpty)
        None
      else
        Some(valueStringToValueWoNull(trimmedText))
    }

  protected def valueStringToValueWoNull(text: String): T =
    displayStringToValueWoNull(text)

  def displayJsonToValue(json: JsReadable): Option[T] =
    json match {
      case JsNull => None
      case JsString(s) => displayStringToValue(s)
      case JsDefined(json) => displayJsonToValue(json)
      case _: JsUndefined => None
      case _ => Some(displayJsonToValueWoString(json))
    }

  protected def displayJsonToValueWoString(json: JsReadable): T
    = throw new AdaConversionException(s"JSON $json is supposed to be a String.")

  def jsonToValue(json: JsReadable): Option[T] =
    json match {
      case JsNull => None
      case _: JsUndefined => None
      case JsDefined(json) => jsonToValue(json)
      case _ => Some(jsonToValueWoNull(json))
    }

  protected def jsonToValueWoNull(json: JsReadable): T

  def valueToJson(value: Option[T]): JsValue =
    value.map(valueToJsonNonEmpty).getOrElse(JsNull)

  protected def valueToJsonNonEmpty(value: T): JsValue

  def valueToDisplayString(value: Option[T]): String =
    value.map(valueToDisplayStringNonEmpty).getOrElse("")

  protected def valueToDisplayStringNonEmpty(value: T): String =
    value.toString

  def displayStringToJson(text: String): JsValue =
    valueToJson(displayStringToValue(text))

  def jsonToDisplayString(json: JsReadable): String =
    valueToDisplayString(jsonToValue(json))

  def jsonToDisplayStringOptional(json: JsReadable): Option[String] =
    jsonToValue(json).map(value => valueToDisplayString(Some(value)))

  def isValueOf[E: TypeTag] =
    typeOf[E].typeSymbol.asClass.equals(valueClass)

  def asValueOf[E] =
    this.asInstanceOf[FieldType[E]]
}

private abstract class FormatFieldType[T: Format] extends FieldType[T] {
  override protected def valueToJsonNonEmpty(value: T): JsValue = Json.toJson(value)
}