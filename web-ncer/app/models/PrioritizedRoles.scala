package models

object PrioritizedRoles {
  val apply = Map(
    0 -> Role.Administrator,
    1 -> Role.Committee,
    2 -> Role.BioBank,
    3 -> Role.Requester
  )
}