package models.security

import models.BSONObjectIdentity
import play.api.libs.json.Json
import play.libs.Scala
import be.objectify.deadbolt.core.models.{Role, Permission, Subject}
import reactivemongo.bson.BSONObjectID


/**
  * Custom user class.
  * Use for user construction.
  * Mainly a container for user-specific information.
  * Extends Subject class by a handful of helpful methods for authentification.
  * For simplicity, Subject.getIdentifier is represented by a String.
  *
  */
//class CustomUser(val email: String, val password: String, userName: String, roles: List[Role], permissions: List[Permission]) extends AbstractUser(userName, roles, permissions) {
case class CustomUser(val firstName: String, val lastName: String, val email: String, val password: String, val roles: List[Role] = List(SecurityRoleCache.basicRole), val permissions: List[Permission] = SecurityPermissionCache.basicPermissions, val _id: Option[BSONObjectID] = None) extends Subject{
  def getMail: String = email
  def getPassword: String = password
  def getID: Option[BSONObjectID] = _id

  // basic methods required by Subject class
  def getIdentifier: String = firstName + lastName
  def getRoles: java.util.List[Role] = Scala.asJava(roles)
  def getPermissions: java.util.List[Permission] = Scala.asJava(permissions)

  //replace string with BSONID
  //def getIdentifier: BSONObjectID = ???

  //def getAffilition: String = ???
  //def getFirstName: String = ???
  //def getLastName: String = ???
  //def getFullName = (getFirstName + " " + getLastName)

  //add later
  //def getSavedFilters: Seq[FilterSpec] = ???
}