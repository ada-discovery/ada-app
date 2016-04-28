package ldap

import com.google.inject.{Inject, Singleton}
import play.api.Configuration

/**
  * Created by jan.martens on 4/28/2016.
  */
@Singleton
class LdapSettings @Inject()(configuration: Configuration) extends Enumeration{
  // be aware that by default, client certificates are disabled and server certificates are always trusted!
  // do not use remote mode unless you know the server you connect to!
  val dit: String = configuration.getString("ldap.dit").getOrElse("cn=accounts,dc=ada")
  val groups: Seq[String] = configuration.getStringSeq("ldap.groups").getOrElse(Seq())

  // switch for local ldap server or connection to remote server
  // use "local" to set up local in-memory server
  // use "remote" to set up connection to remote server
  // use "none" to disable this module completely
  // defaults to "local", if no option is given
  val mode: String = configuration.getString("ldap.mode").getOrElse("local").toLowerCase()
  val addDebugUsers: Boolean = configuration.getBoolean("ldap.debugusers").getOrElse(false)
  val host: String = configuration.getString("ldap.host").getOrElse("localhost")
  val port: Int = configuration.getInt("ldap.port").getOrElse(389)
  val timeout: Int = configuration.getInt("ldap.timeout").getOrElse(2000)
  val bindDN: String = configuration.getString("ldap.bindDN").getOrElse("cn=admin.user,dc=users," + dit)
  val bindPassword: String = configuration.getString("ldap.bindPassword").getOrElse("123456")
  val encryption: String = configuration.getString("ldap.encryption").getOrElse("none").toLowerCase()
  val trustStore: Option[String] = configuration.getString("ldap.trustStore")
  val updateInterval: Int = configuration.getInt("ldap.updateinterval").getOrElse(1800)
}
