package models

import java.util.Date

import org.ada.server.dataaccess.BSONObjectIdentity
import org.ada.server.json.EnumFormat
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

case class BatchRequest(
  _id: Option[BSONObjectID] = None,
  dataSetId: String,
  itemIds: Seq[BSONObjectID],
  state: BatchSampleRequestState.Value,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
)

object BatchSampleRequestState extends Enumeration {
  val SentForApproval, Rejected, Created, Approved, BiobankAcknowledged, Unavailable, Sent, UserReceived,NotReceived = Value
}

object BatchRequest {
  implicit val stateFormat = EnumFormat(BatchSampleRequestState)
  implicit val batchRequestFormat = Json.format[BatchRequest]

  implicit object BatchRequestIdentity extends BSONObjectIdentity[BatchRequest] {
    def of(entity: BatchRequest): Option[BSONObjectID] = entity._id
    protected def set(entity: BatchRequest, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}