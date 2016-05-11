package models.security

import models.BSONObjectIdentity
import play.api.libs.json._
import play.libs.Scala
import be.objectify.deadbolt.core.models.{Role, Permission, Subject}
import reactivemongo.bson.BSONObjectID

import play.modules.reactivemongo.json.BSONFormats._

import ldap.LdapDN


/**
  * Generic user class.
  * Extension of Deadbolt Subject.
  * Generalized to hold _id of arbitrary structure.
  * @see CustomUser
  * @see LdapUser
  */
class GenericUser[T](_id: Option[T], name: String, email: String, password: String, affiliation: String, roles: Seq[String], permissions: Seq[String]) extends Subject{
  // basic methods required by Subject class
  def getIdentifier: String = name
  def getRoles: java.util.List[Role] = Scala.asJava(roles.map(r => SecurityRole(r)))
  def getPermissions: java.util.List[Permission] = Scala.asJava(permissions.map(p => SecurityPermission(p)))

  // TODO replace with unapply
  // usable for views, to retrieve selected information on available fields
  def getProperties: Traversable[String] = Traversable[String](name, email, affiliation)
}

/**
  * Links LDAP DN with entry in MongoDB.
  * Holds extra authorization information not included in LDAP.
  * @param _id BSON ID of entry/ user
  * @param ldapDn LDAP DN of user on LDAP server.
  * @param email Email of user (can be used to send notifications.
  * @param roles Roles for Deadbolt.
  * @param permissions Permissions for Deadbolt.
  */
case class CustomUser(_id: Option[BSONObjectID], ldapDn: String, email: String, roles: Seq[String], permissions: Seq[String])
  extends GenericUser[BSONObjectID](_id, ldapDn, email, "", "", roles, permissions){
  override def getIdentifier: String = ldapDn
}

object CustomUser{
  implicit val format: Format[CustomUser] = Json.format[CustomUser]

  implicit object UserIdentity extends BSONObjectIdentity[CustomUser] {
    def of(entity: CustomUser): Option[BSONObjectID] = entity._id
    protected def set(entity: CustomUser, id: Option[BSONObjectID]) = entity.copy(id)
  }
}


/**
  * Holds information that could be extracted from the LDAP server.
  * @param _id LDAP DN
  * @param name  common name (cn)
  * @param email email address
  * @param affiliation organisatorical unit (ou)
  * @param permissions LDAP groups (memberof)
  */
case class LdapUser(_id: String, name: String, email: String, affiliation: String, permissions: Seq[String])
  extends GenericUser[String](Some(_id), name, email, "", affiliation, Seq[String](), permissions) with LdapDN {
  override def getIdentifier: String = _id
  override def getProperties: Seq[String] = Seq[String](_id, name, email, affiliation, permissions.toString)
  def getDN = _id
}

object LdapUser{
  implicit val format: Format[LdapUser] = Json.format[LdapUser]
}
