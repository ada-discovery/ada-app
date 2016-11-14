package models

import dataaccess.{BSONObjectIdentity, EnumFormat}
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import models.FilterCondition.filterFormat
import java.util.Date
import reactivemongo.play.json.BSONFormats._


case class DataView(
  _id: Option[BSONObjectID],
  name: String,
  default: Boolean,
  filters: Seq[Filter],
  tableColumnNames: Seq[String],
  chartTypes: Seq[FieldChartType],
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
)

object DataView {
  implicit val chartEnumTypeFormat = EnumFormat.enumFormat(ChartType)
  implicit val fieldChartTypeFormat = Json.format[FieldChartType]
  implicit val dataViewFormat = Json.format[DataView]

  implicit object DataViewIdentity extends BSONObjectIdentity[DataView] {
    def of(entity: DataView): Option[BSONObjectID] = entity._id
    protected def set(entity: DataView, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}