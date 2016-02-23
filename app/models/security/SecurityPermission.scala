package models.security

import be.objectify.deadbolt.core.models.Permission
import play.libs.Scala


// TODO: eliminate case classes?
// TODO: include permission descriptions and integrate into GUI.


/**
  * Generalized permission. Use to construct custom permissions.
  * @param value descriptor of the permission. Refer to defintions in SecurityPermissionCache as samples.
  */
case class SecurityPermission(val value: String) extends Permission {
  def getValue: String = value
  def getDescription: String = "A human-readable permission description for the GUI."
}

/**
  * Case class for matching.
  * Permission for viewing data and elements.
  * @param view name of related elements
  */
case class ViewPermission(val view: String) extends Permission {
  def getValue: String = "view." + view
}

/**
  * Case class for matching.
  * Permission to access study.
  * @param study name of the study.
  */
case class StudyPermission(val study: String) extends Permission {
  def getValue: String = "study." + study
}

/**
  * Cached definitions of predefined security permissions.
  * Use these for convenience.
  */
object SecurityPermissionCache {
  lazy val adminPermissions: List[Permission] = List(
    new ViewPermission("data.basic"),                       // permission to view data
    new ViewPermission("data.full"),                        // permission to view extended data, including metadata
    new SecurityPermission("edit.data"),                    // permission to edit data
    new ViewPermission("admin"))                            // permission to access admin controls

  lazy val basicPermissions: List[Permission] = List(
    new ViewPermission("data.basic"))                  // permission to view data
}
