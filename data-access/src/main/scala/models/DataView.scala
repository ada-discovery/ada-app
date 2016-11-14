package models

import dataaccess.{BSONObjectIdentity, EnumFormat}
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import models.DataSetFormattersAndIds.fieldChartTypeFormat
import models.FilterCondition.filterFormat

case class DataView(
  _id: Option[BSONObjectID],
  name: String,
  createdById: Option[BSONObjectID],
  default: Boolean,
  filter: Option[Filter],
  tableColumnNames: Seq[String],
  fieldChartTypes: Seq[FieldChartType]
)

object DataView {
  implicit val viewFormat = Json.format[DataView]

  implicit object DataViewIdentity extends BSONObjectIdentity[DataView] {
    def of(entity: DataView): Option[BSONObjectID] = entity._id
    protected def set(entity: DataView, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}