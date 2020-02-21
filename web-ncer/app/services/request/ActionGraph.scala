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
      Role.BioBank,
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
      RequestAction.Acknowledge,
      BatchRequestState.BioBankAcknowledged,
      Role.BioBank,
      Role.BioBank,
      Seq(Role.Requester)
    ),

    Action(
      BatchRequestState.BioBankAcknowledged,
      RequestAction.Shipped,
      BatchRequestState.InTransit,
      Role.BioBank,
      Role.Requester,
      Seq(Role.BioBank)
    ),

    Action(
      BatchRequestState.BioBankAcknowledged,
      RequestAction.NotAvailable,
      BatchRequestState.NotAvailable,
      Role.BioBank,
      Role.Requester,
      Seq(Role.BioBank),
      needsDescription
    ),

    Action(
      BatchRequestState.InTransit,
      RequestAction.Receive,
      BatchRequestState.Received,
      Role.Requester,
      Role.Requester,
      Seq(Role.BioBank, Role.Requester)
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