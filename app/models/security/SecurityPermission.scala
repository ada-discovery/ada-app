package models.security

import be.objectify.deadbolt.core.models.Permission
import persistence.RepoDef

import scala.collection.immutable.SortedSet

/**
  * Generalized permission. Use to construct custom permissions.
  * @param value descriptor of the permission. Refer to defintions in SecurityPermissionCache for samples.
  */
case class SecurityPermission(val value: String) extends Permission {
  def getValue: String = value

  override def toString() = {
    value.substring(value.indexOf('=')+1, value.indexOf(','))
  }
}
