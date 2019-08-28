package models

// TODO: if it's just a single list of roles you want to have switch "roles" to "apply" or turn "PrioritizedRoles" into a "service" e.g. with functions like getRole(priority: Int) and/or switch to a traits
object PrioritizedRoles {
  val roles = Map(0 -> Role.Administrator, 1 -> Role.Committee, 2 -> Role.Owner, 3 -> Role.Requester)
}