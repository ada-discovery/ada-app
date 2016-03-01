package ldap

import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.listener.InMemoryListenerConfig
import com.unboundid.ldap.sdk.LDAPConnection
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.SearchResultEntry

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import models.security._
import models.security.CustomUser



class LdapUserManager extends UserManager {

  // TODO: dummy user profiles. eventually remove them.
  override val adminUser = new CustomUser(None, "ldap admin user", "admin@mail", "123456", "None", List(SecurityRoleCache.adminRole), SecurityPermissionCache.adminPermissions)
  override val basicUser = new CustomUser(None, "ldap basic user", "basic@mail", "123456", "None", List(SecurityRoleCache.basicRole), SecurityPermissionCache.basicPermissions)

  // configuration
  val defaultPort: Int = 389
  lazy val host: String = "localhost"                                             // url of ldap directory
  lazy val port: Int = 389                                                        // port of ldap directory
  lazy val timeout: Int = 2000                                                    // request timeout
  lazy val bindDn: String = "admin@mail"                                          // overwrite with value from user repo
  lazy val password: String = "123456"                                            // overwrite with value from user repo


  /**
    * TODO: remove later
    *
    * Creates an LDAP dummy server for testing
    * @return dummy server
    */
  def createServer(): InMemoryDirectoryServer = {
    val config = new InMemoryDirectoryServerConfig("dc=org");
    val listenerConfig = new InMemoryListenerConfig("test", null, 12345, null, null, null);
    config.setListenerConfigs(listenerConfig);
    config.setSchema(null); // do not check (attribute) schema
    val server = new InMemoryDirectoryServer(config);
    server.startListening();

    server.add("dn: dc=org", "objectClass: top", "objectClass: domain", "dc: org");
    server.add("dn: dc=geomajas,dc=org", "objectClass: top", "objectClass: domain", "dc: geomajas");
    server.add("dn: dc=roles,dc=geomajas,dc=org", "objectClass: top", "objectClass: domain", "dc: roles");
    server.add("dn: dc=staticsecurity,dc=geomajas,dc=org", "objectClass: top", "objectClass: domain", "dc: staticsecurity");
    server.add("dn: cn=testgroup,dc=roles,dc=geomajas,dc=org", "objectClass: groupOfUniqueNames", "cn: testgroup");

    server.add("dn: cn=user@test.com,dc=staticsecurity,dc=geomajas,dc=org", "objectClass: person", "locale: nl_BE", "sn: NormalUser", "givenName: Joe", "memberOf: cn=testgroup,dc=roles,dc=geomajas,dc=org", "userPassword: password");
    server.add("dn: cn=admin@test.com,dc=staticsecurity,dc=geomajas,dc=org", "objectClass: person", "locale: nl_BE", "sn: Administrator", "givenName: Cindy", "memberOf: cn=testgroup,dc=roles,dc=geomajas,dc=org", "userPassword: password");

    server
  }

  /**
    * User authentification using ldap server.
    *
    * @param email
    * @param password
    * @return
    */
  def authenticate(email: String, password: String): Future[Option[CustomUser]] = {
    val server = createServer

    val conn = server.getConnection
    val entry = conn.getEntry("cn=" + email + ",dc=staticsecurity,dc=geomajas,dc=org")

    val permission = entry.getAttributeValue("sn")
    val retPass = entry.getAttributeValue("userPassword")
    server.shutDown(false)

    val user: Option[CustomUser] = if (retPass.equals(password)) {
      Some(CustomUser(None, email, email, retPass, "", Seq(), Seq(permission)))
    } else {
      None
    }
    Future(user)
  }


  /**
    * TODO: change
    *
    */
  override def findById(id: String): Future[Option[CustomUser]] = {
    val server = createServer
    val conn = server.getConnection
    val entry = conn.getEntry("cn=" + id + ",dc=staticsecurity,dc=geomajas,dc=org")

    val permission = entry.getAttributeValue("sn")
    val retPass = entry.getAttributeValue("userPassword")
    server.shutDown(false)

    val user: Option[CustomUser] = if (retPass.equals(password)) {
      Some(CustomUser(None, id, id, retPass, "", Seq(), Seq(permission)))
    } else {
      None
    }
    Future(user)
  }
}

