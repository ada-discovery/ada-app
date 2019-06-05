package models

import org.ada.web.controllers.EnumStringBindable


object NotificationRole extends Enumeration {
  val Requester, Committee, Owner = Value
}

case class Action(
                   fromState: BatchRequestState.Value,
                   action: RequestAction.Value,
                   toState: BatchRequestState.Value,
                   commentNeeded: Boolean = false,
                   notified: Seq[NotificationRole.Value] = Nil
                 )
