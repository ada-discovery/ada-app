package models

case class RoleWithPriority(val role: Role.Value, val priority: Int)

object PrioritizedRoles {
  val roles = Map( 0 -> Role.Administrator, 1 -> Role.Committee, 2 -> Role.Owner, 3 -> Role.Requester)
}