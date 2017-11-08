package controllers.dataset

import models.{Field, Page, Widget}
import play.api.libs.json.JsObject

case class TableViewData(
  page: Page[JsObject],
  tableFields: Traversable[Field]
)