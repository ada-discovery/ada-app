package models.security

import be.objectify.deadbolt.core.models.Subject
import dataaccess.User
import play.libs.Scala

case class DeadboltUser(user: User) extends Subject {
  override def getIdentifier =
    user.ldapDn

  override def getRoles =
    Scala.asJava(user.roles.map(r => SecurityRole(r)))

  override def getPermissions =
    Scala.asJava(user.permissions.map(SecurityPermission(_)))
}
