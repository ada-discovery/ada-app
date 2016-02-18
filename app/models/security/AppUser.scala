package models.security

import play.libs.Scala
import be.objectify.deadbolt.core.models.Subject

/**
  * Custom user class.
  * Use for specialized user construction.
  *
  */
/*class User(val userName: String, val roles: List[SecurityRole], val permissions: List[SecurityPermission]) extends Subject {

  //0, "basic@mail", "123456", "basic user", SecurityRoleCache.basicRole
  def getIdentifier: String = userName
  def getRoles: java.util.List[SecurityRole] =  Scala.asJava(roles)
  def getPermissions: java.util.List[SecurityPermission] =  Scala.asJava(permissions)
}*/

class CustomUser(val userName: String, val roles: List[SecurityRole], val permissions: List[SecurityPermission]) extends Subject {

  //val basicAccount = Account(0, "basic@mail", "123456", "basic user", SecurityRoleCache.basicRole)
  def getIdentifier: String = userName
  def getRoles: java.util.List[SecurityRole] =  Scala.asJava(roles)
  def getPermissions: java.util.List[SecurityPermission] =  Scala.asJava(permissions)
}

/**
  * Admininstrative user with full permissions.
  * AdminUser may view and manipulate all data.
  *
  */
class AdminUser extends Subject {
  def getIdentifier: String = "Admin"
  def getRoles: java.util.List[SecurityRole] = Scala.asJava(List(SecurityRoleCache.adminRole))
  def getPermissions: java.util.List[SecurityPermission] = SecurityPermissionCache.adminPermissions
}

/**
  * User class with basic permissions.
  * BasicUser is allowed to view data.
  *
  */
class BasicUser extends Subject {
  def getIdentifier: String = "Basic"
  def getRoles: java.util.List[SecurityRole] = Scala.asJava(List(SecurityRoleCache.basicRole))
  def getPermissions: java.util.List[SecurityPermission] = SecurityPermissionCache.basicPermissions
}