package models.security

import be.objectify.deadbolt.core.models.Role

/**
  * Implementation of the security role interface.
  * SecurityRole is just a wrapper for a String.
  *
  */
class SecurityRole(val roleName: String) extends Role {
  def getName: String = roleName
}

/**
  * Cached defintions of predefined security roles.
  * Use these for convenience.
  *
  */
object SecurityRoleCache {
  lazy val adminRole: Role = new SecurityRole("admin")
  lazy val basicRole: Role = new SecurityRole("basic")
}