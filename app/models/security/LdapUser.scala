package models.security

import ldap.LdapDN
import play.api.libs.json.{Json, Format}

/**
  * Holds information that could be extracted from the LDAP server.
  *
  * @param _id LDAP DN
  * @param name  common name (cn)
  * @param email email address
  * @param ou organisatorical unit (ou)
  * @param permissions LDAP groups (memberof)
  */
case class LdapUser(_id: String, name: String, email: String, ou: String, permissions: Seq[String]) extends LdapDN {
  def getDN = _id
}

object LdapUser {
  implicit val ldapUserFormat: Format[LdapUser] = Json.format[LdapUser]
}
