package services.request

import models.{Action, BatchRequestState, Role, RequestAction}
import org.incal.core.util.GroupMapList

object ActionGraph {

  val needsDescription = true

  private val actions: Traversable[Action] = Seq(
    Action(
      BatchRequestState.Created, RequestAction.Submit, BatchRequestState.SentForApproval,Role.Requester, Role.Committee ,Seq(), needsDescription
    ),
    Action(
      BatchRequestState.SentForApproval, RequestAction.Approve, BatchRequestState.Approved, Role.Committee, Role.Owner, Seq(Role.Owner, Role.Requester, Role.Committee)
    ),
    Action(
      BatchRequestState.SentForApproval, RequestAction.Reject, BatchRequestState.Rejected, Role.Committee,Role.Requester, Seq(Role.Committee),needsDescription
    ),
    Action(
      BatchRequestState.Approved, RequestAction.Approve, BatchRequestState.OwnerAcknowledged, Role.Owner,Role.Owner, Seq(Role.Requester)
    ),
    Action(
      BatchRequestState.Approved, RequestAction.NotAvailable, BatchRequestState.Unavailable,Role.Owner,Role.Requester, Seq(Role.Owner), needsDescription
    ),
    Action(
      BatchRequestState.OwnerAcknowledged, RequestAction.Send, BatchRequestState.Sent,Role.Owner,Role.Requester, Seq(Role.Requester, Role.Owner), needsDescription
    ),
    Action(
      BatchRequestState.Sent, RequestAction.Receive, BatchRequestState.UserReceived,Role.Requester,Role.Requester, Seq(Role.Owner)
    ),
    Action(
      BatchRequestState.Sent, RequestAction.NotReceive, BatchRequestState.NotReceived, Role.Requester,Role.Owner, Seq(Role.Owner), needsDescription
    )
  )

  val apply: Map[BatchRequestState.Value, Traversable[Action]] = actions.map { action => (action.fromState, action) }.toGroupMap
}
