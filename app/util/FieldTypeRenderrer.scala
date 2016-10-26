package util

import dataaccess.Field
import play.api.libs.json.JsValue
import play.twirl.api.Html
import dataaccess.FieldTypeHelper

trait FieldTypeRenderer {
  def apply(json: Option[JsValue]): Html
}

private class FieldTypeRendererImpl(field: Field) extends FieldTypeRenderer {
  private val ftf = FieldTypeHelper.fieldTypeFactory
  private val fieldType = ftf(field.fieldTypeSpec)

  override def apply(json: Option[JsValue]): Html = {
    val displayString = json.map(fieldType.jsonToDisplayString).getOrElse("")
    Html(displayString)
  }
}

object FieldTypeRenderer {
  def apply(field: Field): FieldTypeRenderer = new FieldTypeRendererImpl(field)
}