package models

import java.util.Date

import org.ada.server.dataaccess.BSONObjectIdentity
import org.ada.server.models.WidgetSpec
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import org.ada.server.models.DataSetFormattersAndIds.widgetSpecFormat

case class BatchOrderRequestSetting(
  _id: Option[BSONObjectID] = None,
  dataSetId: String,
  timeCreated: Date = new Date(),
  committeeUserIds: Seq[BSONObjectID],
  bioBankUserIds: Seq[BSONObjectID],
  viewId: BSONObjectID
)

object BatchOrderRequestSetting {
  implicit val batchRequestSettingFormat = Json.format[BatchOrderRequestSetting]

  implicit object BatchRequestSettingIdentity extends BSONObjectIdentity[BatchOrderRequestSetting] {
    def of(entity: BatchOrderRequestSetting): Option[BSONObjectID] = entity._id
    protected def set(entity: BatchOrderRequestSetting, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}