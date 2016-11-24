package models

import dataaccess.{BSONObjectIdentity, EnumFormat}
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import models.DataSetFormattersAndIds.statsCalcSpecFormat
import java.util.Date
import play.api.libs.json._
import reactivemongo.play.json.BSONFormats._
import dataaccess.OptionFormat

case class DataView(
  _id: Option[BSONObjectID],
  name: String,
  filterIds: Seq[Option[BSONObjectID]],
  tableColumnNames: Seq[String],
  statsCalcSpecs: Seq[StatsCalcSpec],
  elementGridWidth: Int = 3,
  default: Boolean = false,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
)

object DataView {
  // TODO: weird that we had to use a custom option format here..
  implicit val optionalBSONObjectIdFormat = new OptionFormat[BSONObjectID]
  implicit val dataViewFormat = Json.format[DataView]

  implicit object DataViewIdentity extends BSONObjectIdentity[DataView] {
    def of(entity: DataView): Option[BSONObjectID] = entity._id
    protected def set(entity: DataView, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  def applyMain(
    tableColumnNames: Seq[String],
    distributionChartFieldNames: Seq[String],
    elementGridWidth: Int
  ) =
    DataView(
      None,
      "Main",
      Nil,
      tableColumnNames,
      distributionChartFieldNames.map(DistributionCalcSpec(_, None)),
      elementGridWidth,
      true
    )
}