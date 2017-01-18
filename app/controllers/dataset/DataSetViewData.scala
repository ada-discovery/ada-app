package controllers.dataset

import models.{FieldChartSpec, Page, Field}
import play.api.libs.json.JsObject

case class DataSetViewData(
  page: Page[JsObject],
  fieldChartSpecs: Traversable[FieldChartSpec],
  tableFields: Traversable[Field]
)
