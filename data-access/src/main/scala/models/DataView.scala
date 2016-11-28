package models

import dataaccess.{User, BSONObjectIdentity, EnumFormat, OptionFormat}
import play.api.libs.functional.syntax._
import reactivemongo.bson.BSONObjectID
import models.DataSetFormattersAndIds.statsCalcSpecFormat
import java.util.Date
import play.api.libs.json._
import reactivemongo.play.json.BSONFormats._

case class DataView(
  _id: Option[BSONObjectID],
  name: String,
  filterIds: Seq[Option[BSONObjectID]],
  tableColumnNames: Seq[String],
  statsCalcSpecs: Seq[StatsCalcSpec],
  elementGridWidth: Int = 3,
  default: Boolean = false,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date(),
  var createdBy: Option[User] = None
)

object DataView {
  // TODO: weird that we had to use a custom option format here..
  implicit val optionalBSONObjectIdFormat = new OptionFormat[BSONObjectID]
  implicit val dataViewFormat : Format[DataView] = (
    (__ \ "_id").formatNullable[BSONObjectID] and
    (__ \ "name").format[String] and
    (__ \ "filterIds").format[Seq[Option[BSONObjectID]]] and
    (__ \ "tableColumnNames").format[Seq[String]] and
    (__ \ "statsCalcSpecs").format[Seq[StatsCalcSpec]] and
    (__ \ "elementGridWidth").format[Int] and
    (__ \ "default").format[Boolean] and
    (__ \ "createdById").formatNullable[BSONObjectID] and
    (__ \ "timeCreated").format[Date]
  )(
    DataView(_, _, _, _, _, _, _, _, _),
    (item: DataView) =>  (item._id, item.name, item.filterIds, item.tableColumnNames, item.statsCalcSpecs, item.elementGridWidth, item.default, item.createdById, item.timeCreated)
  )

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