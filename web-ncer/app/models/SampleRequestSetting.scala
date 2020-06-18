package models

import org.ada.server.dataaccess.BSONObjectIdentity
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID

case class SampleRequestSetting(
  _id: Option[BSONObjectID] = None,
  dataSetId: String,
  viewId: BSONObjectID
)

object SampleRequestSetting {
  implicit val batchRequestSettingFormat = Json.format[SampleRequestSetting]

  implicit object BatchRequestSettingIdentity extends BSONObjectIdentity[SampleRequestSetting] {
    def of(entity: SampleRequestSetting): Option[BSONObjectID] = entity._id
    protected def set(entity: SampleRequestSetting, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}