package models.security

import be.objectify.deadbolt.core.models.Permission


// TODO: eliminate case classes?
// TODO: include permission descriptions and integrate into GUI.


/**
  * Generalized permission. Use to construct custom permissions.
  * @param value descriptor of the permission. Refer to defintions in SecurityPermissionCache for samples.
  */
case class SecurityPermission(val value: String) extends Permission {
  def getValue: String = value
}

/**
  * Cached definitions of predefined security permissions.
  * Use these for convenience.
  */
object SecurityPermissionCache {
  lazy val adminPermissions: Seq[String] = getPermissions
  lazy val basicPermissions: Seq[String] = Seq("view.data.basic")

  // sequence of all roles
  def getPermissions: Seq[String] = Seq("view.data.basic", "view.data.full", "edit.data" , "view.admin")
}
