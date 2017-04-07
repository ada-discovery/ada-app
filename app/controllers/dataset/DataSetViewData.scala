package controllers.dataset

import models.{Field, Page, Widget}
import play.api.libs.json.JsObject

case class DataSetViewData(
  page: Page[JsObject],
  widgets: Traversable[Widget],
  tableFields: Traversable[Field]
)