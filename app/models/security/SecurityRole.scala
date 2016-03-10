package models.security

import be.objectify.deadbolt.core.models.Role

/**
  * Implementation of the security role interface.
  * SecurityRole is just a wrapper for a String.
  *
  */
case class SecurityRole(val roleName: String) extends Role {
  def getName: String = roleName
}

/**
  * Cached defintions of predefined security roles.
  * Use these for convenience.
  *
  */
object SecurityRoleCache {
  lazy val adminRole: String = "admin"
  lazy val basicRole: String = "basic"
  lazy val undefined: String = "none"               // dummy if no role defined

  // sequence of all roles
  def getRoles: Seq[String] = Seq(basicRole, adminRole, undefined)
}