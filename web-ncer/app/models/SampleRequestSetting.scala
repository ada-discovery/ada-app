package models

import org.ada.server.dataaccess.BSONObjectIdentity
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

case class SampleRequestSetting(
  _id: Option[BSONObjectID] = None,
  dataSetId: String,
  viewId: BSONObjectID
)

object SampleRequestSetting {
  implicit val sampleRequestSettingFormat = Json.format[SampleRequestSetting]

  implicit object SampleRequestSettingIdentity extends BSONObjectIdentity[SampleRequestSetting] {
    def of(entity: SampleRequestSetting): Option[BSONObjectID] = entity._id
    protected def set(entity: SampleRequestSetting, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}