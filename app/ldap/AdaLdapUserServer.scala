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

  val ldapinterface: LDAPInterface

  def addUsersFromRepo(interface: LDAPInterface, userRepo: UserRepo): Unit
  def getUsers(interface: LDAPInterface): Seq[CustomUser]

  def setupInterface(): LDAPInterface
  def terminateInterface(interface: LDAPInterface): Unit

  def getEntryList: List[String]
}


@Singleton
class AdaLdapUserServerImpl @Inject()(applicationLifecycle: ApplicationLifecycle, configuration: Configuration) extends AdaLdapUserServer{

  // root of ldap tree
  val dit = "dc=ncer"

  //switch for local ldap server or connection to remote server
  val mode: String = configuration.getString("ldap.mode").getOrElse("local")

  // local server options
  // port for listener
  val listenerPort: Int = configuration.getInt("ldap.port").getOrElse(389)


  // options for connecting oto remote server
  // general config for connection setup
  val defaultPort: Int = configuration.getInt("ldap.port").getOrElse(389)
  val defaultHost: String = configuration.getString("ldap.host").getOrElse("localhost")
  val serverTimeout: Int = configuration.getInt("ldap.timeout").getOrElse(2000)
  // configuration for authorized binding
  val defaultbindDn: String = "cn=" + adminUser.email + ",dc=users," + dit
  val defaultPassword: String = adminUser.password


  override val ldapinterface: LDAPInterface = setupInterface


  /**
    * Creates either a server or a connection, depending on the configuration.
    * @return LDAPInterface, either of type InMemoryDirectoryServer or LDAPConnection.
    */
  override def setupInterface(): LDAPInterface = {
    val interface = mode match{
      case "local" => createServer
      case "remote" => createConnection
      case _ => createServer
    }
    // hook interface in lifecycle for proper cleanup
    applicationLifecycle.addStopHook{ () =>
      Future(terminateInterface(interface))
    }
    interface
  }


  /**
    * Creates branches for users, permissions and roles in ldap tree.
    */
  def createTree(interface: LDAPInterface): Unit = {
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
  def addPermissions(interface: LDAPInterface): Unit = {
    val dc : String = "dc=permissions," + dit
    val permissions: Seq[String] = SecurityPermissionCache.getPermissions
    permissions.foreach{p: String =>
      interface.add("dn: dc=" + p + "," + dc, "objectClass: permission", "objectClass: top", "dc: "+ p)
    }
  }

  /**
    * Add cached roles to InMemoryDirectoryServer and build flat role tree.
    */
  def addRoles(interface: LDAPInterface): Unit = {
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
  def createServer(): InMemoryDirectoryServer = {
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
    server
  }

  /**
    * Creates a connection to an existing LDAP server instance.
    * Uses the options defined in the configuation.
    * @return LDAPConnection object pointing with specified credentials.
    */
  def createConnection(): LDAPConnection = {
    //val options: LDAPConnectionOptions = new LDAPConnectionOptions()
    val connection = new LDAPConnection(defaultHost, defaultPort, defaultbindDn, defaultPassword)
    connection
  }

  /**
    * Closes LDAPConnection or shuts down InMemoryDirectoryServer.
    * Ensures that application releases ports.
    * @param interface Interface to be disconnected or shut down.
    */
  override def terminateInterface(interface: LDAPInterface): Unit = {
    ldapinterface match{
      case server: InMemoryDirectoryServer => server.shutDown(true)
      case connection: LDAPConnection => connection.close()
      case _ => Unit
    }
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
    val entry: SearchResultEntry = ldapinterface.getEntry("cn=" + email + ",dc=users," + dit)
    val user: Option[CustomUser] = LdapUtil.entryToUser(entry)
    Future(user)
  }


  /**
    * For debugging purposes.
    * Gets list of all entries.
    * @return
    */
  override def getEntryList: List[String] = {
    LdapUtil.getEntryList(ldapinterface)
    //List[String]()
  }


}
