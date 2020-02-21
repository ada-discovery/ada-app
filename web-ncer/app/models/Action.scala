package models

object Role extends Enumeration {
  val Requester, Committee, BioBank, Administrator = Value
}

case class Action(
  fromState: BatchRequestState.Value,
  action: RequestAction.Value,
  toState: BatchRequestState.Value,
  allowed: Role.Value,
  solicited: Role.Value,
  notified: Seq[Role.Value] = Nil,
  commentNeeded: Boolean = false
)