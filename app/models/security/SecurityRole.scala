package models.security

import be.objectify.deadbolt.core.models.Role



class SecurityRole(val roleName: String) extends Role
{
  //case class AdminUser extends SecurityRole("admin")
  //case class DefaultUser extends SecurityRole("default")

  def getName: String = roleName
}