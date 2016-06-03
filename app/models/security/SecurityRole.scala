package models.security

import be.objectify.deadbolt.core.models.Role

/**
  * Implementation of the security role interface.
  * SecurityRole is just a wrapper for a String.
  *
  */
case class SecurityRole(val roleName: String) extends Role {
  override def getName: String = roleName
}

/**
  * Cached defintions of predefined security roles.
  * Use these for convenience.
  *
  */
object SecurityRoles {
  val adminRole = "admin"
  val basicRole = "basic"
}