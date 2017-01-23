package models

import play.api.libs.json.{JsObject, Json}

case class JsTreeNode(
  id: String,
  parent: String,
  text: String,
  `type`: Option[String] = None,
  data: Option[JsObject] = None
  // 'state' : { 'opened' : true, 'selected' : true },
)

object JsTreeNode {
  implicit val format = Json.format[JsTreeNode]

  def fromCategory(category: Category) =
    JsTreeNode(
      category._id.map(_.stringify).getOrElse(""),
      category.parentId.map(_.stringify).getOrElse("#"),
      category.labelOrElseName,
      Some("category"),
      Some(Json.obj("label" -> category.label))
    )

  def fromField(field: Field) =
    JsTreeNode(
      field.name,
      field.categoryId.map(_.stringify).getOrElse("#"),
      field.labelOrElseName,
      Some("field"),
      Some(Json.obj("label" -> field.label))
    )
}