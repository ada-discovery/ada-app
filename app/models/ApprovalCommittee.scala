package models

import java.util.Date

import org.ada.server.dataaccess.BSONObjectIdentity
import org.ada.server.json.EnumFormat
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

import java.util.Date

import org.ada.server.dataaccess.BSONObjectIdentity
import org.ada.server.json.EnumFormat
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

case class ApprovalCommittee(
                              _id: Option[BSONObjectID] = None,
                              fullName: String,
                              email: String,
                              institute: String
)

object ApprovalCommittee {
  implicit val aprovalCommitteeFormat = Json.format[ApprovalCommittee]

  implicit object ApprovalCommitteeIdentity extends BSONObjectIdentity[ApprovalCommittee] {
    def of(entity: ApprovalCommittee): Option[BSONObjectID] = entity._id
    protected def set(entity: ApprovalCommittee, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}