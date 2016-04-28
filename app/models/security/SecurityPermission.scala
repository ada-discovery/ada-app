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
    println("called")
    value.substring(value.indexOf('=')+1, value.indexOf(','))
  }
}

/**
  * Cached definitions of predefined security permissions.
  * Use these for convenience.
  */
object SecurityPermissionCache {
  lazy val adminPermissions: Seq[String] = getPermissions
  lazy val basicPermissions: Seq[String] = Seq("view.data.basic")

  // create permissions fo repo data based on registered repos
  def createRepoPermissions: List[String] = {
    val repos: List[String] = RepoDef.values.map{ (r: RepoDef.Value) => r.toString}.toList
    repos.flatMap{r: String => (
      "view."+r+".data" ::
      "view."+r+".metadata" ::
      "edit."+r+".data" ::
      "edit."+r+".metadata" ::
      Nil)}
  }

  // sequence of all roles
  lazy val getPermissions: Seq[String] = (List("view.admin") ++ createRepoPermissions)
}
