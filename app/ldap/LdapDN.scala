package ldap

import com.unboundid.ldap.sdk.DN

/**
  * TODO temporary
  */
trait LdapDN {
  def getDN: DN
}
