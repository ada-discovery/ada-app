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
  state: BatchRequestState.Value = BatchRequestState.Draft,
  createdById: BSONObjectID,
  timeCreated: Date = new Date(),
  history: Seq[ActionInfo] = Nil
)

case class ActionInfo(
  fromState:BatchRequestState.Value,
  toState: BatchRequestState.Value,
  performedByUser: String,
  comment: Option[String] = None,
  timestamp: Date = new Date()
)

object BatchRequestState extends Enumeration {
  val Draft,  // [START] Initial state of a request
  AwaitingApproval,  // Awaiting approval by committee
  Rejected,  // [END] Committee has rejected the request
  Approved,  // Committee has approved the request
  BioBankAcknowledged,  // Sample owner acknowledged the approved request
  NotAvailable,  // [END] Sample owner does not have any of the requested samples
  InTransit,  // Sample owner shipped some or all samples to requester
  Received,  // [END] Sample owner confirms arrival
  None  // used internally as default
  = Value
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