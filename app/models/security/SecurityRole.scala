package models.security

import be.objectify.deadbolt.core.models.Role


class SecurityRole(val roleName: String) extends Role
{
  case class AdminRole() extends SecurityRole("Administrator")
  case class DefaultRole() extends SecurityRole("DefaultUser")

  def getName: String = roleName

  def valueOf(value: String): Role = value match {
    case "Administrator" => AdminRole()
    case "DefaultUser"    => DefaultRole()
    case _               => throw new IllegalArgumentException()
  }
}