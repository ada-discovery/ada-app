package models.security

import be.objectify.deadbolt.core.models.Permission
import play.libs.Scala

/**
 *
 * @author Steve Chaloner (steve@objectify.be)
 */

class SecurityPermission(val value: String) extends Permission {
  def getValue: String = value
}

/**
  * Cached defintions of predefined security permissions.
  * Use these for convenience.
  *
  */
object SecurityPermissionCache {
  lazy val adminPermissions = List(
    new SecurityPermission("view.data.basic"),
    new SecurityPermission("view.data.full"),
    new SecurityPermission("view.admin"))

  lazy val basicPermissions = List(
    new SecurityPermission("view.data.basic"))
}
