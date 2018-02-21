package models

import dataaccess._
import play.api.libs.functional.syntax._
import reactivemongo.bson.BSONObjectID
import models.DataSetFormattersAndIds.widgetSpecFormat
import java.util.Date

import models.json.{EitherFormat, OptionFormat}
import play.api.libs.json._
import reactivemongo.play.json.BSONFormats._

case class DataView(
  _id: Option[BSONObjectID],
  name: String,
  filterOrIds: Seq[Either[Seq[models.FilterCondition], BSONObjectID]],
  tableColumnNames: Seq[String],
  widgetSpecs: Seq[WidgetSpec],
  elementGridWidth: Int = 3,
  default: Boolean = false,
  isPrivate: Boolean = false,
  useOptimizedRepoChartCalcMethod: Boolean = false,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date(),
  var createdBy: Option[User] = None
)

object DataView {

  implicit val eitherFormat = EitherFormat[Seq[models.FilterCondition], BSONObjectID]

  implicit val dataViewFormat : Format[DataView] = (
    (__ \ "_id").formatNullable[BSONObjectID] and
    (__ \ "name").format[String] and
    (__ \ "filterOrIds").format[Seq[Either[Seq[models.FilterCondition], BSONObjectID]]] and
    (__ \ "tableColumnNames").format[Seq[String]] and
    (__ \ "widgetSpecs").format[Seq[WidgetSpec]] and
    (__ \ "elementGridWidth").format[Int] and
    (__ \ "default").format[Boolean] and
    (__ \ "isPrivate").format[Boolean] and
    (__ \ "useOptimizedRepoChartCalcMethod").format[Boolean] and
    (__ \ "createdById").formatNullable[BSONObjectID] and
    (__ \ "timeCreated").format[Date]
  )(
    DataView(_, _, _, _, _, _, _, _, _, _, _),
    (item: DataView) =>  (
      item._id,
      item.name,
      item.filterOrIds,
      item.tableColumnNames,
      item.widgetSpecs,
      item.elementGridWidth,
      item.default,
      item.isPrivate,
      item.useOptimizedRepoChartCalcMethod,
      item.createdById,
      item.timeCreated)
  )

  implicit object DataViewIdentity extends BSONObjectIdentity[DataView] {
    def of(entity: DataView): Option[BSONObjectID] = entity._id
    protected def set(entity: DataView, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  def applyMain(
    tableColumnNames: Seq[String],
    distributionChartFieldNames: Seq[String],
    elementGridWidth: Int,
    useOptimizedRepoChartCalcMethod: Boolean
  ) =
    DataView(
      None,
      "Main",
      Nil,
      tableColumnNames,
      distributionChartFieldNames.map(DistributionWidgetSpec(_, None)),
      elementGridWidth,
      true,
      false,
      useOptimizedRepoChartCalcMethod
    )
}