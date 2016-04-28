package models.security

import models.BSONObjectIdentity
import play.api.libs.json._
import play.libs.Scala
import be.objectify.deadbolt.core.models.{Role, Permission, Subject}
import reactivemongo.bson.BSONObjectID

import play.modules.reactivemongo.json.BSONFormats._
import com.unboundid.ldap.sdk._

import ldap.LdapDN


/**
  * Custom user class.
  * Use for user construction.
  * Mainly a container for user-specific information.
  * Extends Subject class by a handful of helpful methods for authentification.
  * For simplicity, Subject.getIdentifier is represented by a String.
  */
case class CustomUser(_id: Option[BSONObjectID], name: String, email: String, password: String, affiliation: String, roles: Seq[String], permissions: Seq[String]) extends Subject with LdapDN{

  // basic methods required by Subject class
  def getIdentifier: String = name
  def getRoles: java.util.List[Role] = Scala.asJava(roles.map(r => SecurityRole(r)))
  def getPermissions: java.util.List[Permission] = Scala.asJava(permissions.map(p => SecurityPermission(p)))

  // get DN for ldap
  def getDN = new DN(name)

  // usable for views, to retrieve infos on available fields
  // replace with unapply
  def getProperties: Seq[String] = Seq[String](name, email, affiliation)
}

object CustomUser{
  implicit val format: Format[CustomUser] = Json.format[CustomUser]

  implicit object UserIdentity extends BSONObjectIdentity[CustomUser] {
    def of(entity: CustomUser): Option[BSONObjectID] = entity._id
    protected def set(entity: CustomUser, id: Option[BSONObjectID]) = entity.copy(id)
  }
}