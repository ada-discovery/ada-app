package models.security

import be.objectify.deadbolt.core.models.Permission


// TODO: eliminate case classes?
// TODO: include permission descriptions and integrate into GUI.


/**
  * Generalized permission. Use to construct custom permissions.
  * @param value descriptor of the permission. Refer to defintions in SecurityPermissionCache as samples.
  */
case class SecurityPermission(val value: String) extends Permission {
  def getValue: String = value
}

/**
  * Cached definitions of predefined security permissions.
  * Use these for convenience.
  */
object SecurityPermissionCache {
  lazy val adminPermissions: List[String] = List(
    "view.data.basic",                                                    // permission to view data
    "view.data.full",                                                     // permission to view extended data, including metadata
    "edit.data" ,                                                         // permission to edit data
    "view.admin")                                                         // permission to access admin controls

  lazy val basicPermissions: List[String] = List("view.data.basic")
}
