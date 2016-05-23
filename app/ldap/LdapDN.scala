package ldap

import play.api.libs.json.JsValue

/**
  * Trait for use with LdapRepo.
  * Provide ldap dn and json converter for filtering.
  */
trait LdapDN {
  def getDN: String
  def toJson: JsValue
}
