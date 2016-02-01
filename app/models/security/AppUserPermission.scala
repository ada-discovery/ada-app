package models.security

import be.objectify.deadbolt.core.models.Permission

/**
 *
 * @author Steve Chaloner (steve@objectify.be)
 */

class AppUserPermission(val value: String) extends Permission
{
  def getValue: String = value
}
