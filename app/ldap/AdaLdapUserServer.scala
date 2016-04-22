package ldap

import java.util.Calendar
import javax.net.ssl.{SSLContext, SSLSocketFactory}

import com.google.inject.{Inject, ImplementedBy, Singleton}
import com.unboundid.ldap.listener.{InMemoryListenerConfig, InMemoryDirectoryServer, InMemoryDirectoryServerConfig}
import com.unboundid.ldap.sdk._
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest
import com.unboundid.util.ssl.{TrustStoreTrustManager, TrustAllTrustManager, SSLUtil}
import models.workspace.UserGroup

import persistence.RepoTypes._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.FiniteDuration

import models.security.{UserManager, SecurityPermissionCache, SecurityRoleCache, CustomUser}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._

import play.api.inject.ApplicationLifecycle
import play.api.{Logger, Configuration}


/**
  * Create Ldap user server or use an established connection.
  * See options in ldap.conf for settings.
  * Users from the current user repo can be imported.
  */
@ImplementedBy(classOf[AdaLdapUserServerImpl])
trait AdaLdapUserServer extends UserManager{

  val ldapinterface: Option[LDAPInterface]

  def addUsersFromRepo(interface: LDAPInterface, userRepo: UserRepo): Unit

  def getUserGroups(interface: LDAPInterface): Traversable[UserGroup]
  def getUsers(interface: LDAPInterface): Traversable[CustomUser]

  def setupInterface(): Option[LDAPInterface]
  def terminateInterface(interface: Option[LDAPInterface]): Unit

  // debug
  def getEntryList: Traversable[String]
  def getUserList: Traversable[CustomUser]
  def getUserGroupList: Traversable[UserGroup]




  def getUsers: Traversable[CustomUser]
  def getUserGroups: Traversable[UserGroup]

  // used for lazy updating
  def update: Boolean
  def needsUpdate: Boolean
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
  // use "none" to disable this module completely
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

  // list of groups to be used
  val groups: Seq[String] = configuration.getStringSeq("ldap.groups").getOrElse(Seq())

  // interval for lazy updates
  val updateInterval: Int = configuration.getInt("ldap.updateinterval").getOrElse(1800)

  // interface to be used; can be an InMemoryDirectoryServer or a Connection/ConnectionPool
  override val ldapinterface: Option[LDAPInterface] = setupInterface




  var userCache: Traversable[CustomUser] = Traversable()
  var userGroupCache: Traversable[UserGroup] = Traversable()
  var lastUpdate: Long = 0//currentTime
  def currentTime: Long = (Calendar.getInstance().getTimeInMillis() / 1000)         // current time in seconds


  def needsUpdate: Boolean = {
    ((currentTime - lastUpdate) > updateInterval)
  }

  override def getUsers: Traversable[CustomUser] = {
    if(needsUpdate)
      update

    userCache
  }

  override def getUserGroups: Traversable[UserGroup] = {
    if(needsUpdate)
      update

    userGroupCache
  }


  override def update: Boolean = {
    val newUserCache: Traversable[CustomUser] = getUserList
    val newUserGroupCache: Traversable[UserGroup] = getUserGroupList
    if(!(newUserCache.isEmpty || newUserGroupCache.isEmpty)){
      lastUpdate = currentTime
      userCache = newUserCache
      userGroupCache = newUserGroupCache
      Logger.info("ldap cache updated")
      true
    }else{
      Logger.warn("ldap cache update failed")
      false
    }
  }


  /**
    * Creates either a server or a connection, depending on the configuration.
    * @return LDAPInterface, either of type InMemoryDirectoryServer or LDAPConnection.
    */
  override def setupInterface(): Option[LDAPInterface] = {
    val interface = mode match{
      case "local" => Some(createServer)
      case "remote" => createConnection()
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
    interface.add("dn: " + dit, "objectClass:top", "objectClass:domain", dit.replace("=",":"))
    // add subtrees: roles, permissions, people
    interface.add("dn:dc=roles," + dit, "objectClass:top", "objectClass:domain", "dc:roles")
    interface.add("dn:dc=permissions," + dit, "objectClass:top", "objectClass:domain", "dc:permissions")
    interface.add("dn:dc=users," + dit, "objectClass:top", "objectClass:domain", "dc:users")
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
    * Utility method for adding users.
    * @param interface
    * @param user
    */
  protected def addUser(interface: LDAPInterface, user: CustomUser): Unit = {
    interface.add(LdapUtil.userToEntry(user))
  }


  /**
    * Establish connection and check if bind possible.
    * Close connection afterwards.
    * @param userDN
    * @param password
    * @return
    */
  protected def canBind(userDN: String, password: String): Boolean ={
    val conn: Option[LDAPConnectionPool] = createConnection(userDN, password)
    if(conn.isDefined){
      conn.get.close()
      true
    }else{
      false
    }
  }

  /**
    * Authenticate by checking if mail exists, then trying bind operation.
    * @param email Mail for matching.
    * @param password Password which should match the password associated to the mail.
    * @return None, if password is wrong or not associated mail was found. Corresponding Account otherwise.
    */
  override def authenticate(email: String, password: String): Future[Boolean] = {
    findByEmail(email).map{ usrOp:Option[CustomUser] => usrOp match{
        case Some(usr) => canBind(usr.name, password)
        case None => false
      }
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
    * @param bind custom bindDn; loaded from config if not defined.
    * @param pw custom password; loaded from config if not defined.
    * @return LDAPConnection object  with specified credentials. None, if no connection could be established.
    */
  def createConnection(bind: String = bindDn, pw: String = bindPassword): Option[LDAPConnectionPool] = {
    val sslUtil: SSLUtil = setupSSLUtil
    encryption match{
      case "ssl" => {
        // connect to server with ssl encryption
        val sslSocketFactory: SSLSocketFactory = sslUtil.createSSLSocketFactory()
        val connection: LDAPConnection = new LDAPConnection(sslSocketFactory, defaultHost, defaultPort)
        val result: ResultCode = try{
          connection.bind(bind, pw).getResultCode
        }catch{case _: Throwable => ResultCode.NO_SUCH_OBJECT}
        if(result == ResultCode.SUCCESS)
          Some(new LDAPConnectionPool(connection, 1, 10))
        else
          None
      }
      case "starttls" => {
        // connect to server with starttls connection
        val connection: LDAPConnection = new LDAPConnection(defaultHost, defaultPort)
        val result: ResultCode = try{
          connection.bind(bind, pw).getResultCode
        }catch{case _: Throwable => ResultCode.NO_SUCH_OBJECT}
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
          connection.bind(bind, pw).getResultCode
        }catch{case _: Throwable => ResultCode.NO_SUCH_OBJECT}
        if(result == ResultCode.SUCCESS)
          Some(new LDAPConnectionPool(connection, 1, 10))
        else
          None
      }
    }
  }

  /**
    * Setup SSL context (e.g for use with startTLS).
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
  override def getUsers(interface: LDAPInterface): Traversable[CustomUser] = {
    val baseDN ="cn=users," + dit
    val scope = SearchScope.SUB
    val filter = Filter.createEqualityFilter("objectClass", "top")
    val request: SearchRequest = new SearchRequest(baseDN, scope, filter)

    val entries: Traversable[Entry] = dipatchSearchRequest(request)
    LdapUtil.convertAndFilter(entries, LdapUtil.entryToUser)
  }


  override def getUserGroups(interface: LDAPInterface): Traversable[UserGroup] = {
    val baseDN ="cn=groups," + dit
    val scope = SearchScope.SUB
    val filter = Filter.createEqualityFilter("objectClass", "groupofnames")
    val request: SearchRequest = new SearchRequest(baseDN, scope, filter)

    val entries: Traversable[Entry] = dipatchSearchRequest(request)
    LdapUtil.convertAndFilter(entries, LdapUtil.entryToUserGroup)
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

  /**
    * Find first user matching specified uuid. Checks branch given by dit.
    * @param id uuid of user.
    * @return Future(None), if no user found; CustomUser wrappend in Option and Future else.
    */
  override def findById(id: String): Future[Option[CustomUser]] = {
    val user: Option[CustomUser] = ldapinterface match{
      case Some(interface) => {
        val entry: SearchResultEntry = interface.getEntry("uid=" + id + "," +  dit)
        LdapUtil.entryToUser(entry)
      }
      case None => None
    }
    Future(user)
  }

  /**
    * Find user with designated mail. Returns first valid match, if multiple ones exist.
    * Constructs CustomUser object from result.
    * @param email String of associated user mail.
    * @return CustomUser, if found, None else
    */
  override def findByEmail(email: String): Future[Option[CustomUser]] = {
    val user: Option[CustomUser] = ldapinterface match{
      case Some(interface) => {
        //val entry: SearchResultEntry = interface.getEntry(dit, "email="+email)
        val request: SearchRequest = new SearchRequest(dit, SearchScope.SUB, Filter.createEqualityFilter("mail", email))
        val result: SearchResult = interface.search(request)
        if(result.getEntryCount == 0){
          None
        }else{
          val entry: Entry = result.getSearchEntries.get(0)
          LdapUtil.entryToUser(entry)
        }
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
  override def getEntryList: Traversable[String] = {
    ldapinterface match{
      case Some(interface) => LdapUtil.getEntryList(interface, dit)
      case None => Traversable()
    }
  }

  /**
    *
    * @return
    */
  override def getUserGroupList: Traversable[UserGroup] = {
    ldapinterface match{
      case Some(interface) => getUserGroups(interface)
      case None => Traversable()
    }
  }

  /**
    *
    * @return
    */
  override def getUserList: Traversable[CustomUser] = {
    ldapinterface match{
      case Some(interface) => getUsers(interface)
      case None => Traversable()
    }
  }

  /**
    * Secure, crash-safe ldap search method.
    * @param request SearchRequest to be executed.
    * @return List of search results. Empty, if request failed.
    */
  def dipatchSearchRequest(request: SearchRequest): Traversable[Entry] = {
    try {
      ldapinterface match{
        case Some(interface) => {
          val result: SearchResult = interface.search(request)
          result.getSearchEntries
        }
        case None => Traversable[Entry]()
      }
    }catch{case e: Throwable => Traversable[Entry]()}
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
