package controllers

import models._
import play.api.libs.json._

case class ManifestedFormat[E: Manifest](format: Format[E]){
  val man = manifest[E]
}

class SubTypeFormat[T](formats: Traversable[ManifestedFormat[_ <: T]]) extends Format[T] {

  private val concreteClassFieldName = "concreteClass"

  val classNameFormatMap = formats.map{ classAndFormat => (
    classAndFormat.man.runtimeClass.getName,
    classAndFormat.format.asInstanceOf[Format[T]])
  }.toMap

  override def reads(json: JsValue): JsResult[T] = {
    val concreteClassName = (json \ concreteClassFieldName).get.as[String]
    val format = classNameFormatMap.getOrElse(concreteClassName,
      throw new AdaException(s"Json Formatter for a sub type '$concreteClassName' not recognized."))
    format.reads(json)
  }

  override def writes(o: T): JsValue = {
    val concreteClassName = o.getClass.getName
    val format = classNameFormatMap.getOrElse(concreteClassName,
      throw new AdaException(s"Json Formatter for a sub type '$concreteClassName' not recognized."))
    val json = format.writes(o).asInstanceOf[JsObject]
    json + (concreteClassFieldName, JsString(concreteClassName))
  }
}