package models

import org.ada.server.dataaccess.BSONObjectIdentity
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

case class ApprovalCommittee(
  _id: Option[BSONObjectID] = None,
  dataSetId: String,
  userIds: Seq[BSONObjectID]
 // userIds: UserIds
)

//case class UserIds(userIds: Seq[BSONObjectID])

object ApprovalCommittee {
 // implicit val userIdsFormat = Json.format[UserIds]
  implicit val approvalCommitteeFormat = Json.format[ApprovalCommittee]

  implicit object ApprovalCommitteeIdentity extends BSONObjectIdentity[ApprovalCommittee] {
    def of(entity: ApprovalCommittee): Option[BSONObjectID] = entity._id
    protected def set(entity: ApprovalCommittee, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}