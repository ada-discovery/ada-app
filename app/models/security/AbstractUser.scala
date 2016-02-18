package models.security

import play.libs.Scala
import be.objectify.deadbolt.core.models.Subject

/**
  * Abstract User class.
  * Mainly a container for user-specific information.
  * Extends Subject class by a handful of helpful methods for authentification.
  *
  */
abstract class AbstractUser(val userName: String, val roles: List[SecurityRole], val permissions: List[SecurityPermission]) extends Subject {
  // basic methods required by Subject class
  def getIdentifier: String = userName
  def getRoles: java.util.List[SecurityRole] = Scala.asJava(roles)
  def getPermissions: java.util.List[SecurityPermission] = Scala.asJava(permissions)

  // additional fields
  def getMail: String = ???
  def getPassword: String = ???
}

/**
  * Custom user class.
  * Use for user construction.
  *
  */
class CustomUser(val email: String, val password: String, userName: String, roles: List[SecurityRole], permissions: List[SecurityPermission]) extends AbstractUser(userName, roles, permissions) {
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