package controllers.dataset

import org.incal.play.Page
import models._
import play.api.libs.json.JsObject

case class TableViewData(
  page: Page[JsObject],
  filter: Option[Filter],
  tableFields: Traversable[Field]
)