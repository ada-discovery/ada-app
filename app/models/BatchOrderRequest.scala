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
                              itemIds: String,
                              state: BatchRequestState.Value = BatchRequestState.Created,
                              createdById: Option[BSONObjectID] = None,
                              createdByName: Option[String] = None,
                              timeCreated: Date = new Date(),
                              history: Option[TrackingHistory] = None
                            )
case class TrackingHistory(actionInfo: List[ActionInfo])
case class BatchRequestAction(performedById: BSONObjectID, fromState:BatchRequestState.Value, toState: BatchRequestState.Value)
case class ActionInfo(timestamp: Date, action: BatchRequestAction, comment: Option[String])

object BatchRequestState extends Enumeration {
  val SentForApproval, Rejected, Created, Approved, OwnerAcknowledged, Unavailable, Sent, UserReceived, NotReceived = Value
}

object BatchOrderRequest {
  implicit val stateFormat = EnumFormat(BatchRequestState)


  //  implicit val objectIdOptionFormat = Json.format[Option[BSONObjectID]]
  //  implicit val objectIdsFormat = Json.format[ Seq[BSONObjectID]]
  implicit val requestActionFormat = Json.format[BatchRequestAction]
  implicit val actionInfoFormat = Json.format[ActionInfo]
  implicit val historyFormat = Json.format[TrackingHistory]
  implicit val batchRequestFormat = Json.format[BatchOrderRequest]

  implicit object BatchRequestIdentity extends BSONObjectIdentity[BatchOrderRequest] {
    def of(entity: BatchOrderRequest): Option[BSONObjectID] = entity._id

    protected def set(entity: BatchOrderRequest, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}