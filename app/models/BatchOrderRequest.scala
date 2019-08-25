package models

import java.util.Date

import org.ada.server.dataaccess.BSONObjectIdentity
import org.ada.server.json.EnumFormat
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import reactivemongo.play.json.JSONSerializationPack._

case class BatchOrderRequest(
  _id: Option[BSONObjectID] = None,
  dataSetId: String,
  itemIds: Seq[BSONObjectID],
  state: BatchRequestState.Value = BatchRequestState.Created,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date(),
  history: Seq[ActionInfo] = Nil
)

case class ActionInfo(
  timestamp: Date,
  performedByUser: String,
  fromState:BatchRequestState.Value,
  toState: BatchRequestState.Value,
  comment: Option[String]
)

object BatchRequestState extends Enumeration {
  val SentForApproval, Rejected, Created, Approved, OwnerAcknowledged, Unavailable, Sent, UserReceived, NotReceived, Error, None = Value
}

object BatchOrderRequest {
  implicit val stateFormat = EnumFormat(BatchRequestState)
  implicit val actionInfoFormat = Json.format[ActionInfo]
  implicit val batchRequestFormat = Json.format[BatchOrderRequest]

  implicit object BatchRequestIdentity extends BSONObjectIdentity[BatchOrderRequest] {
    def of(entity: BatchOrderRequest): Option[BSONObjectID] = entity._id

    protected def set(entity: BatchOrderRequest, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}