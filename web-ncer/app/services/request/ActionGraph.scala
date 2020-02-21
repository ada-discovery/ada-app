package services.request

import models.{Action, BatchRequestState, Role, RequestAction}
import org.incal.core.util.GroupMapList

object ActionGraph {

  val needsDescription = true

  private val actions: Traversable[Action] = Seq(
    Action(
      BatchRequestState.Draft,
      RequestAction.Submit,
      BatchRequestState.AwaitingApproval,
      Role.Requester,
      Role.Committee,
      Seq(Role.Requester),
      needsDescription
    ),

    Action(
      BatchRequestState.AwaitingApproval,
      RequestAction.Approve,
      BatchRequestState.Approved,
      Role.Committee,
      Role.Owner,
      Seq(Role.Requester, Role.Committee)
    ),

    Action(
      BatchRequestState.AwaitingApproval,
      RequestAction.Reject,
      BatchRequestState.Rejected,
      Role.Committee,
      Role.Requester,
      Seq(Role.Committee),
      needsDescription
    ),

    Action(
      BatchRequestState.Approved,
      RequestAction.Approve,
      BatchRequestState.BioBankAcknowledged,
      Role.Owner,
      Role.Owner,
      Seq(Role.Requester)
    ),

    Action(
      BatchRequestState.BioBankAcknowledged,
      RequestAction.Send,
      BatchRequestState.InTransit,
      Role.Owner,
      Role.Requester,
      Seq(Role.Owner),
      needsDescription
    ),

    Action(
      BatchRequestState.BioBankAcknowledged,
      RequestAction.NotAvailable,
      BatchRequestState.NotAvailable,
      Role.Owner,
      Role.Requester,
      Seq(Role.Owner),
      needsDescription
    ),

    Action(
      BatchRequestState.InTransit,
      RequestAction.Receive,
      BatchRequestState.Received,
      Role.Requester,
      Role.Requester,
      Seq(Role.Owner, Role.Requester)
    )
  )

  val asMap: Map[BatchRequestState.Value, Traversable[Action]] =
    actions.map { action => (action.fromState, action) }.toGroupMap

  val createAction =
    Action(
      BatchRequestState.None,
      RequestAction.Create,
      BatchRequestState.Draft,
      Role.Requester,
      Role.Requester
    )
}