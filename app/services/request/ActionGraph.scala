package services.request

import models.{Action, BatchRequestState, NotificationRole, RequestAction}
import org.incal.core.util.GroupMapList

object ActionGraph {

  private val actions: Traversable[Action] = Seq(
    Action(
      BatchRequestState.Created, RequestAction.Submit, BatchRequestState.SentForApproval, true, Seq(NotificationRole.Owner, NotificationRole.Committee)
    ),
    Action(
      BatchRequestState.SentForApproval, RequestAction.Approve, BatchRequestState.Approved
    ),
    Action(
      BatchRequestState.Created, RequestAction.Approve, BatchRequestState.SentForApproval, true
    ),
    Action(
      BatchRequestState.Created, RequestAction.Approve, BatchRequestState.SentForApproval, true
    ),
    Action(
      BatchRequestState.Created, RequestAction.Approve, BatchRequestState.SentForApproval, true
    ),
    Action(
      BatchRequestState.Created, RequestAction.Approve, BatchRequestState.SentForApproval, true
    )
  )

  val apply: Map[BatchRequestState.Value, Traversable[Action]] = actions.map { action => (action.fromState, action) }.toGroupMap
}
