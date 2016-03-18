package ldap


import com.google.inject.{Inject, ImplementedBy, Singleton}
import com.unboundid.ldap.listener.{InMemoryListenerConfig, InMemoryDirectoryServer, InMemoryDirectoryServerConfig}
import com.unboundid.ldap.sdk._

import util.SecurityUtil
import persistence.RepoTypes._

import scala.concurrent.duration._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.FiniteDuration

import models.security.{UserManager, SecurityPermissionCache, SecurityRoleCache, CustomUser}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._

import play.api.inject.ApplicationLifecycle
import play.api.Configuration



/**
  * Create Ldap user server or use an established connection..
  * If no LDAPInterface is used, an InMemoryDirectoryServer based is created.
  * Users from the current user repo are sued.
  */
@ImplementedBy(classOf[AdaLdapUserServerImpl])
trait AdaLdapUserServer extends UserManager{

  val ldapServer: InMemoryDirectoryServer

  def createTree(interface: LDAPInterface): Unit
  def addPermissions(interface: LDAPInterface): Unit
  def addRoles(sinterface: LDAPInterface): Unit

  def addUsersFromRepo(interface: LDAPInterface, userRepo: UserRepo): Unit
  def createServer(): InMemoryDirectoryServer
  def authorize(userid: String, permission: String): Boolean
  def getUsers(interface: LDAPInterface): Seq[CustomUser]

  def shutdown(server: InMemoryDirectoryServer): Unit


  def getEntryList: List[String]
}


@Singleton
class AdaLdapUserServerImpl @Inject()(applicationLifecycle: ApplicationLifecycle, configuration: Configuration) extends AdaLdapUserServer{

  // root of ldap tree
  val dit = "dc=ncer"

  // port for listener
  val listenerPort: Int = configuration.getInt("ldap.port").getOrElse(389)

  // not used yet: options, if connection ot existing ldap server should be used.
  // to be used if no InMemoryServer is supposed to be used.
  val defaultPort: Int = configuration.getInt("ldap.port").getOrElse(389)
  val defaultHost: String = configuration.getString("ldap.host").getOrElse("localhost")
  // bind configuration for connecting
  val defaultbindDn: String = "cn=" + adminUser.email + ",dc=users," + dit
  val defaultPassword: String = adminUser.password
  val serverTimeout: Int = configuration.getInt("ldap.timeout").getOrElse(2000)


  override val ldapServer: InMemoryDirectoryServer = createServer

  /**
    * Creates branches for users, permissions and roles in ldap tree.
    */
  override def createTree(interface: LDAPInterface): Unit = {
    // add root
    interface.add("dn: " + dit, "objectClass: top", "objectClass: domain", dit.replace("=",":"))
    // add subtrees: roles, permissions, people
    interface.add("dn: dc=roles," + dit, "objectClass: top", "objectClass: domain", "dc: roles")
    interface.add("dn: dc=permissions," + dit, "objectClass: top", "objectClass: domain", "dc: permissions")
    interface.add("dn: dc=users," + dit, "objectClass: top", "objectClass: domain", "dc: users")
  }

  /**
    * Add cached roles to InMemoryDirectoryServer and build flat permission tree.
    */
  override def addPermissions(interface: LDAPInterface): Unit = {
    val dc : String = "dc=permissions," + dit
    val permissions: Seq[String] = SecurityPermissionCache.getPermissions
    permissions.foreach{p: String =>
      interface.add("dn: dc=" + p + "," + dc, "objectClass: permission", "objectClass: top", "dc: "+ p)
    }
  }

  /**
    * Add cached roles to InMemoryDirectoryServer and build flat role tree.
    */
  override def addRoles(interface: LDAPInterface): Unit = {
    val dc : String = "dc=roles," + dit
    val roles: Seq[String] = SecurityRoleCache.getRoles
    roles.foreach{r: String =>
      val cn: String = "cn=" + r + ","
      interface.add("dn: "+cn+dc, "objectClass: group", "objectClass: top", "dc: "+r)
    }
  }

  /**
    * Fetches users from database and inserts them into ldap object
    * @param userRepo rep from which users are to be extr4acted and added
    */
  override def addUsersFromRepo(interface: LDAPInterface, userRepo: UserRepo): Unit = {
    val timeout: FiniteDuration = 120000 millis
    val usersFuture: Future[Traversable[CustomUser]] = userRepo.find()
    val users: Traversable[CustomUser] = Await.result(usersFuture, timeout)
    users.foreach{user: CustomUser =>
      interface.add(LdapUtil.userToEntry(user))
    }
  }

  /**
    * Creates an LDAP inmemory server for testing.
    * Builds user permissions and roles from PermissionCache and RoleCache.
    * Feed users from user database into server.
    * @return dummy server
    */
  override def createServer(): InMemoryDirectoryServer = {
    // setup configuration
    val config = new InMemoryDirectoryServerConfig(dit);
    config.setSchema(null); // do not check (attribute) schema
    config.setAuthenticationRequiredOperationTypes(OperationType.DELETE, OperationType.ADD, OperationType.MODIFY, OperationType.MODIFY_DN)

    // required for interaction; commented out for debugging reasons

    val listenerConfig = new InMemoryListenerConfig("defaultListener", null, listenerPort, null, null, null);
    config.setListenerConfigs(listenerConfig);

    val server = new InMemoryDirectoryServer(config);
    server.startListening();

    // initialize ldap structures
    createTree(server)
    addPermissions(server)
    addRoles(server)

    // add default dummy users
    server.add(LdapUtil.userToEntry(basicUser))
    server.add(LdapUtil.userToEntry(adminUser))

    applicationLifecycle.addStopHook{ () =>
      Future(shutdown(ldapServer))
    }

    server
  }

  /**
    * Checks if user has given permission.
    * @param userid User name to be checked.
    * @param permission Permission to be checked.
    * @return true, if the user exists and has given permission associated.
    */
  override def authorize(userid: String, permission: String): Boolean = {
    val timeout: FiniteDuration = 120000 millis
    val userFuture: Future[Option[CustomUser]] = findById(userid)
    val resFuture = userFuture.map{userOp =>
      if(userOp.isDefined){
        val user = userOp.get
        user.permissions.contains(permission)
      }else{
        false
      }
    }
    Await.result(resFuture, timeout)
  }


  /**
    * Reconstruct a sequence of customUsers from users registered in ldap server.
    * @return Seq[CustomUser] for use in other modules.
    */
  override def getUsers(interface: LDAPInterface): Seq[CustomUser] = {
    val baseDN ="dc=users," + dit
    val scope = SearchScope.SUB
    val filter = Filter.createEqualityFilter("objectClass", "person")
    val request: SearchRequest = new SearchRequest(baseDN, scope, filter)

    val result: SearchResult = interface.search(request)
    val entries: List[Entry] = result.getSearchEntries.toList
    val userOps: List[Option[CustomUser]] = entries.map{LdapUtil.entryToUser}
    userOps.filter{ user => user.isDefined}.map{ user => user.get}
  }

  /**
    * Check if user exists and match passwords.
    * @param email Mail of user to be checked.
    * @param password Password to be matched with user password.
    * @return true, if user exists and password is correct.
    */
  override def authenticate(email: String, password: String): Future[Boolean] = {
    val pwHash: String = SecurityUtil.md5(password)
    val userFuture: Future[Option[CustomUser]] = findByEmail(email)
    userFuture.map { userOp: Option[CustomUser] =>
      if (userOp.isDefined){
        val userPw: String = userOp.get.password
        userPw == pwHash
      }else {
        false
      }
    }
  }

  // forward to findByEmail
  override def findById(id: String): Future[Option[CustomUser]] = {
    findByEmail(id)
  }

  /**
    * Find user with designated mail in InMemoryServer.
    * Construct CustomUser object from result if possible.
    * @param email String of associated user mail
    * @return CustomUser, if found, None else
    */
  override def findByEmail(email: String): Future[Option[CustomUser]] = {
    val entry: SearchResultEntry = ldapServer.getEntry("cn=" + email + ",dc=users," + dit)
    val user: Option[CustomUser] = LdapUtil.entryToUser(entry)
    Future(user)
  }


  override def shutdown(server: InMemoryDirectoryServer): Unit = {
    server.shutDown(true)
  }


  override def getEntryList: List[String] = {
    LdapUtil.getEntryList(ldapServer)
  }

}
