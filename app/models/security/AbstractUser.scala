package models.security

import models.BSONObjectIdentity
import play.api.libs.json.Json
import play.libs.Scala
import be.objectify.deadbolt.core.models.{Role, Permission, Subject}
import reactivemongo.bson.BSONObjectID


/*object AbstractUser {

  def apply() = None
  def apply(value: String) = None
  def apply(value1: String, value2: String) = None

  implicit val UserFormat = Json.format[AbstractUser]

  implicit object UserIdentity extends BSONObjectIdentity[AbstractUser] {
    def of(entity: AbstractUser): Option[BSONObjectID] = entity._id
    protected def set(entity: AbstractUser, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}*/




/**
  * Abstract User class.
  * Mainly a container for user-specific information.
  * Extends Subject class by a handful of helpful methods for authentification.
  * For simplicity, Subject.getIdentifier is represented by a String.
  */
abstract class AbstractUser(val userName: String, val roles: List[Role], val permissions: List[Permission]) extends Subject {
  // basic methods required by Subject class
  def getIdentifier: String = userName
  def getRoles: java.util.List[Role] = Scala.asJava(roles)
  def getPermissions: java.util.List[Permission] = Scala.asJava(permissions)

  // additional fields
  def getMail: String = ???
  def getPassword: String = ???




  def _id = None
  //def _id = BSONObjectID("empoty")

  //replace string with BSONID
  //def getIdentifier: BSONObjectID = ???

  //def getAffilition: String = ???
  //def getFirstName: String = ???
  //def getLastName: String = ???
  //def getFullName = (getFirstName + " " + getLastName)

  //add later
  //def getSavedFilters: Seq[FilterSpec] = ???
}

/**
  * Custom user class.
  * Use for user construction.
  *
  */
//class CustomUser(val email: String, val password: String, userName: String, roles: List[Role], permissions: List[Permission]) extends AbstractUser(userName, roles, permissions) {
class CustomUser(val email: String, val password: String, userName: String) extends AbstractUser(userName, List(SecurityRoleCache.basicRole), SecurityPermissionCache.basicPermissions) {
  override def getMail: String = email
  override def getPassword: String = password
}

/**
  * TODO: This is a dummy user profile.
  * Admininstrative user with full permissions.
  * AdminUser may view and manipulate all data.
  *
  */
case class AdminUser() extends AbstractUser("admin", List(SecurityRoleCache.adminRole), SecurityPermissionCache.adminPermissions) {
  override def getMail: String = "admin@mail"
  override def getPassword: String = "123456"
}

/**
  * TODO: This is a dummy user profile.
  * User class with basic permissions.
  * BasicUser is allowed to view data.
  *
  */
case class BasicUser() extends AbstractUser("basic", List(SecurityRoleCache.basicRole), SecurityPermissionCache.basicPermissions) {
  override def getMail: String = "basic@mail"
  override def getPassword: String = "123456"
}