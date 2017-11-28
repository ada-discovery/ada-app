package models.security

import be.objectify.deadbolt.scala.models.Subject
import dataaccess.User

case class DeadboltUser(user: User) extends Subject {
  override def identifier =
    user.ldapDn

  override def roles =
    user.roles.map(SecurityRole(_)).toList

  override def permissions =
    user.permissions.map(SecurityPermission(_)).toList
}
