package ldap


import javax.net.ssl.{SSLContext, SSLSocketFactory}

import com.google.inject.{Inject, ImplementedBy, Singleton}
import com.unboundid.ldap.listener.{InMemoryListenerConfig, InMemoryDirectoryServer, InMemoryDirectoryServerConfig}
import com.unboundid.ldap.sdk._
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest
import com.unboundid.util.ssl.{TrustStoreTrustManager, TrustAllTrustManager, SSLUtil}

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
  * Create Ldap user server or use an established connection.
  * See options in ldap.conf for settings.
  * Users from the current user repo can be imported.
  */
@ImplementedBy(classOf[AdaLdapUserServerImpl])
trait AdaLdapUserServer extends UserManager{

  val ldapinterface: Option[LDAPInterface]

  def addUsersFromRepo(interface: LDAPInterface, userRepo: UserRepo): Unit
  def getUsers(interface: LDAPInterface): Seq[CustomUser]

  def setupInterface(): Option[LDAPInterface]
  def terminateInterface(interface: Option[LDAPInterface]): Unit

  def getEntryList: List[String]
}


@Singleton
class AdaLdapUserServerImpl @Inject()(applicationLifecycle: ApplicationLifecycle, configuration: Configuration) extends AdaLdapUserServer{

  // be aware that by default, client certificates are disabled and server certificates are always trusted!
  // do not use remote mode unless you know the server you connect to!

  // root of ldap tree
  val dit = configuration.getString("ldap.dit").getOrElse("dc=ncer")

  // switch for local ldap server or connection to remote server
  // use "local" to set up local in-memory server
  // use "remote" to set up connection to remote server
  // this flag defaults to "local", if no option is given
  val mode: String = configuration.getString("ldap.mode").getOrElse("local").toLowerCase()

  // local server options
  // port for listener
  val listenerPort: Int = configuration.getInt("ldap.port").getOrElse(389)

  // options for connecting to remote server
  // general config for connection setup
  val defaultPort: Int = configuration.getInt("ldap.port").getOrElse(389)
  val defaultHost: String = configuration.getString("ldap.host").getOrElse("localhost")
  val serverTimeout: Int = configuration.getInt("ldap.timeout").getOrElse(2000)
  // configuration for authorized binding
  val bindDn: String = configuration.getString("ldap.bindDN").getOrElse("cn=" + adminUser.email + ",dc=users," + dit)
  val bindPassword: String = configuration.getString("ldap.bindPassword").getOrElse(adminUser.password)
  val encryption: String = configuration.getString("ldap.encryption").getOrElse("none").toLowerCase()
  val trustStorePath: Option[String] = configuration.getString("ldap.trustStore")

  override val ldapinterface: Option[LDAPInterface] = setupInterface


  /**
    * Creates either a server or a connection, depending on the configuration.
    * @return LDAPInterface, either of type InMemoryDirectoryServer or LDAPConnection.
    */
  override def setupInterface(): Option[LDAPInterface] = {
    val interface = mode match{
      case "local" => Some(createServer)
      case "remote" => createConnection
      case _ => None
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
    * @param interface LDAPInterface to operate on.
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
    * @param interface LDAPInterface to operate on.
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
    * @param interface LDAPInterface to operate on.
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
    server.add(LdapUtil.userToEntry(basicUser, dit))
    server.add(LdapUtil.userToEntry(adminUser, dit))
    server
  }

  /**
    * Creates a connection to an existing LDAP server instance.
    * Uses the options defined in the configuation.
    * Used options from configuration are ldap.encryption, ldap.host, ldap.prt, ldap.bindDN, ldap.bindPassword
    * @return LDAPConnection object  with specified credentials. None, if no connection could be established.
    */
  def createConnection(): Option[LDAPConnectionPool] = {
    val sslUtil: SSLUtil = setupSSLUtil
    encryption match{
      case "ssl" => {
        // connect to server with ssl encryption
        val sslSocketFactory: SSLSocketFactory = sslUtil.createSSLSocketFactory()
        val connection: LDAPConnection = new LDAPConnection(sslSocketFactory, defaultHost, defaultPort)
        val result: ResultCode = try{
          connection.bind(bindDn, bindPassword).getResultCode
        }catch{case _ => ResultCode.NO_SUCH_OBJECT}
        if(result == ResultCode.SUCCESS)
          Some(new LDAPConnectionPool(connection, 1, 10))
        else
          None
      }
      case "starttls" => {
        // connect to server with starttls connection
        val connection: LDAPConnection = new LDAPConnection(defaultHost, defaultPort)
        val result: ResultCode = try{
          connection.bind(bindDn, bindPassword).getResultCode
        }catch{case _ => ResultCode.NO_SUCH_OBJECT}
        val sslContext: SSLContext = sslUtil.createSSLContext()
        connection.processExtendedOperation(new StartTLSExtendedRequest(sslContext))
        val processor: StartTLSPostConnectProcessor = new StartTLSPostConnectProcessor(sslContext)
        if(result == ResultCode.SUCCESS)
          Some(new LDAPConnectionPool(connection, 1, 10, processor))
        else
          None
      }
      case _ =>{
        // create unsecured connection
        val connection: LDAPConnection = new LDAPConnection(defaultHost, defaultPort)
        val result: ResultCode = try{
          connection.bind(bindDn, bindPassword).getResultCode
        }catch{case _ => ResultCode.NO_SUCH_OBJECT}
        if(result == ResultCode.SUCCESS)
          Some(new LDAPConnectionPool(connection, 1, 10))
        else
          None
      }
    }
  }

  /**
    * Setup SSL context (e.g) for use with startTLS.
    * If a truststore file has been defined in the config, it will be loaded.
    * Otherwise, server certificates will be blindly trusted.
    * @return Created SSLContext.
    */
  protected def setupSSLUtil(): SSLUtil = {
    trustStorePath match{
      case Some(path) => new SSLUtil(new TrustStoreTrustManager(path))
      case None => new SSLUtil(new TrustAllTrustManager())
    }
  }


  /**
    * Closes LDAPConnection or shuts down InMemoryDirectoryServer.
    * Ensures that application releases ports.
    * @param interface Interface to be disconnected or shut down.
    */
  override def terminateInterface(interface: Option[LDAPInterface]): Unit = {
    if(interface.isDefined){
      interface.get match{
        case server: InMemoryDirectoryServer => server.shutDown(true)
        case connection: LDAPConnection => connection.close()
        case connectionPool: LDAPConnectionPool => connectionPool.close()
        case _ => Unit
      }
    }
  }

  /**
    * Reconstruct a sequence of customUsers from users registered in ldap server.
    * @return Seq[CustomUser] for use in other modules.
    */
  override def getUsers(interface: LDAPInterface): Seq[CustomUser] = {
    val baseDN ="dc=users," + dit
    val scope = SearchScope.SUB
    val filter = Filter.createEqualityFilter("cn", "users")
    val request: SearchRequest = new SearchRequest(baseDN, scope, filter)

    val result: SearchResult = interface.search(request)
    val entries: List[Entry] = result.getSearchEntries.toList
    val userOps: List[Option[CustomUser]] = entries.map{LdapUtil.entryToUser}
    userOps.filter{ user => user.isDefined}.map{ user => user.get}
  }

  /**
    * Find specific DN as user.
    * @param dn String defining the full DN.
    * @return CustomUser wrapped in option.
    */
  def findByDN(dn: String): Option[CustomUser] = {
    ldapinterface match{
      case Some(interface) => {
        val entry: SearchResultEntry = interface.getEntry(dn)
        LdapUtil.entryToUser(entry)
      }
      case None => None
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
    val user: Option[CustomUser] = ldapinterface match{
      case Some(interface) => {
        val entry: SearchResultEntry = interface.getEntry("cn=" + email + ",dc=users," + dit)

        LdapUtil.entryToUser(entry)
      }
      case None => None
    }
    Future(user)
  }

  /**
    * For debugging purposes.
    * Gets list of all entries.
    * @return
    */
  override def getEntryList: List[String] = {
    ldapinterface match{
      case Some(interface) => LdapUtil.getEntryList(interface, dit)
      case None => List()
    }
  }

  /**
    * Update specified user. The user id is used for querying the user, the other fields are updated.
    * TODO: This updates all fields, even if they did not change. Lazy updates should be preferred.
    * @param user CustomUser to be updated.
    * @return true, if user found and updated.
    */
  override def updateUser(user: CustomUser): Future[Boolean] = {
    // lookup user and terminate early, if not present
    val entry = ldapinterface match{
      case Some(interface) => interface.getEntry("cn=" + user.email + ",dc=users," + dit)
      case None => null
    }

    if(entry == null)
      return Future(false)

    val dn: String = entry.getDN
    val permissions: String = if(user.permissions.isEmpty){"none"}else{user.permissions.head}
    val roles: String = if (user.roles.isEmpty){"none"}else{user.roles.head}

    val modSN: Modification = new Modification(
      ModificationType.REPLACE,
      "sn",
      user.name
    )

    val modCN: Modification = new Modification(
      ModificationType.REPLACE,
      "cn",
      user.name
    )

    val modO: Modification = new Modification(
      ModificationType.REPLACE,
      "o",
      user.affiliation
    )

    val modMemberOf = new Modification(
      ModificationType.REPLACE,
      "memberOf",
      roles + permissions
    )

    val request: ModifyRequest = new ModifyRequest(dn, modSN, modCN, modO, modMemberOf)
    ldapinterface match{
      case Some(interface) => {
        val response = interface.modify(request)
        val success = response.getResultCode == ResultCode.SUCCESS
        Future(success)
      }
      case None => Future(true)
    }

  }

}
