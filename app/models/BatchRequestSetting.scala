package models

import org.ada.server.dataaccess.BSONObjectIdentity
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

case class BatchRequestSetting(
  _id: Option[BSONObjectID] = None,
  dataSetId: String,
  userIds: Seq[BSONObjectID],
  displayFieldNames: Seq[String]
)

object BatchRequestSetting {
  implicit val approvalCommitteeFormat = Json.format[BatchRequestSetting]

  implicit object ApprovalCommitteeIdentity extends BSONObjectIdentity[BatchRequestSetting] {
    def of(entity: BatchRequestSetting): Option[BSONObjectID] = entity._id
    protected def set(entity: BatchRequestSetting, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}