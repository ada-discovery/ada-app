package models

import java.util.Date

import org.ada.server.dataaccess.BSONObjectIdentity
import org.ada.server.models.WidgetSpec
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import org.ada.server.models.DataSetFormattersAndIds.widgetSpecFormat

case class BatchRequestSetting(
  _id: Option[BSONObjectID] = None,
  dataSetId: String,
  timeCreated: Date = new Date(),
  widgetSpecs: Seq[WidgetSpec],
  userIds: Seq[BSONObjectID],
  displayFieldNames: Seq[String]
)

object BatchRequestSetting {
  implicit val approvalCommitteeFormat = Json.format[BatchRequestSetting]

  implicit object BatchRequestSettingIdentity extends BSONObjectIdentity[BatchRequestSetting] {
    def of(entity: BatchRequestSetting): Option[BSONObjectID] = entity._id
    protected def set(entity: BatchRequestSetting, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}