package services.request

import models.{Action, BatchRequestState, NotificationRole, RequestAction}
import org.incal.core.util.GroupMapList

object ActionGraph {

  val needsDescription = true

  private val actions: Traversable[Action] = Seq(
    Action(
      BatchRequestState.Created, RequestAction.Submit, BatchRequestState.SentForApproval, Seq(NotificationRole.Requester, NotificationRole.Committee), needsDescription
    ),
    Action(
      BatchRequestState.SentForApproval, RequestAction.Approve, BatchRequestState.Approved, Seq(NotificationRole.Owner, NotificationRole.Requester, NotificationRole.Committee)
    ),
    Action(
      BatchRequestState.SentForApproval, RequestAction.Reject, BatchRequestState.Rejected, Seq(NotificationRole.Requester, NotificationRole.Committee),needsDescription
    ),
    Action(
      BatchRequestState.Approved, RequestAction.Approve, BatchRequestState.OwnerAcknowledged, Seq(NotificationRole.Requester, NotificationRole.Owner)
    ),
    Action(
      BatchRequestState.Approved, RequestAction.NotAvailable, BatchRequestState.Unavailable, Seq(NotificationRole.Requester, NotificationRole.Owner), needsDescription
    ),
    Action(
      BatchRequestState.OwnerAcknowledged, RequestAction.Send, BatchRequestState.Sent, Seq(NotificationRole.Requester, NotificationRole.Owner), needsDescription
    ),
    Action(
      BatchRequestState.Sent, RequestAction.Receive, BatchRequestState.UserReceived, Seq(NotificationRole.Requester, NotificationRole.Owner)
    ),
    Action(
      BatchRequestState.Sent, RequestAction.NotReceive, BatchRequestState.NotReceived,Seq(NotificationRole.Requester, NotificationRole.Owner), needsDescription
    )
  )

  val apply: Map[BatchRequestState.Value, Traversable[Action]] = actions.map { action => (action.fromState, action) }.toGroupMap
}
