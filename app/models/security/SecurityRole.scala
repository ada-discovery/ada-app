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
  * Definitions of predefined security roles.
  * Use these for convenience.
  *
  */
object SecurityRole {
  val admin = "admin"
  val basic = "basic"
}