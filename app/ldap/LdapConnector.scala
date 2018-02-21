package ldap

import javax.net.ssl.{SSLContext, SSLSocketFactory}

import com.google.inject.{ImplementedBy, Singleton, Inject}

import com.unboundid.ldap.listener.{InMemoryListenerConfig, InMemoryDirectoryServer, InMemoryDirectoryServerConfig}
import com.unboundid.ldap.sdk._
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest
import com.unboundid.util.ssl.{TrustStoreTrustManager, TrustAllTrustManager, SSLUtil}

import play.api.Logger
import play.api.inject.ApplicationLifecycle

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[LdapConnectorImpl])
trait LdapConnector {
  val ldapinterface: Option[LDAPInterface]
  val ldapsettings: LdapSettings

  def getEntryList: Traversable[String]
  def findByDN(dn: String): Option[Entry]
  def dispatchRequest(searchRequest: SearchRequest): Traversable[Entry]
  def canBind(userDN: String, password: String): Boolean
}

@Singleton
class LdapConnectorImpl @Inject()(applicationLifecycle: ApplicationLifecycle, settings: LdapSettings) extends LdapConnector {
  // interface for use
  val ldapinterface: Option[LDAPInterface] = setupInterface()
  val ldapsettings: LdapSettings = settings

  private val logger = Logger

  /**
    * Creates either a server or a connection, depending on the configuration.
    * @return LDAPInterface, either of type InMemoryDirectoryServer or LDAPConnection.
    */
  private def setupInterface(): Option[LDAPInterface] = {
    val interface = settings.mode match{
      case "local" => Some(createServer)
      case "remote" => createConnectionPool()
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
  protected def createTree(interface: LDAPInterface): Unit = {
    // add root
    interface.add("dn: " + settings.dit, "objectClass:top", "objectClass:domain", settings.dit.replace("=",":"))
    // add subtrees: roles, permissions, people
    interface.add("dn:dc=groups," + settings.dit, "objectClass:top", "objectClass:domain", "dc:roles")
    interface.add("dn:dc=users," + settings.dit, "objectClass:top", "objectClass:domain", "dc:users")
  }

  /**
    * Add cached roles to InMemoryDirectoryServer and build flat permission tree.
    */
//  protected def addGroups(interface: LDAPInterface): Unit = {
//    val dc : String = "dc=groups," + settings.dit
//    val permissions: Seq[String] = SecurityPermissionCache.getPermissions
//    permissions.foreach{p: String =>
//      interface.add("dn: dc=" + p + "," + dc, "objectClass:top", "objectClass:groupofnames", "dc: "+ p)
//    }
//  }

  /**
    * Creates an LDAP inmemory server for testing.
    * Builds user permissions and roles from PermissionCache and RoleCache.
    * Feed users from user database into server.
    * @return dummy server
    */
  private def createServer(): InMemoryDirectoryServer = {
    // setup configuration
    val config = new InMemoryDirectoryServerConfig(settings.dit);
    config.setSchema(null); // do not check (attribute) schema
    config.setAuthenticationRequiredOperationTypes(OperationType.DELETE, OperationType.ADD, OperationType.MODIFY, OperationType.MODIFY_DN)

    // required for interaction; commented out for debugging reasons
    val listenerConfig = new InMemoryListenerConfig("defaultListener", null, settings.port, null, null, null);
    config.setListenerConfigs(listenerConfig);

    val server = new InMemoryDirectoryServer(config);
    server.startListening();

    // initialize ldap structures
    createTree(server)
//    addGroups(server)

    // add default dummy users
    /*
    if(settings.addDebugUsers){
      server.add(LdapUtil.userToEntry(basicUser, dit))
      server.add(LdapUtil.userToEntry(adminUser, dit))
    }
      */
    server
  }

  /**
    * Creates a connection to an existing LDAP server instance.
    * We use ConnectionPools for better performance.
    * Uses the options defined in the configuation.
    * Used options from configuration are ldap.encryption, ldap.host, ldap.prt, ldap.bindDN, ldap.bindPassword
    * @param bind custom bindDn; loaded from config if not defined.
    * @param pw custom password; loaded from config if not defined.
    * @return LDAPConnection object  with specified credentials. None, if no connection could be established.
    */
  protected def createConnectionPool(
    bind: String = settings.bindDN,
    pw: String = settings.bindPassword
  ): Option[LDAPConnectionPool] = {
    val (connection, processor) = createConnection

    val result: ResultCode = try {
      connection.bind(bind, pw).getResultCode
    } catch {
      case _: Throwable => ResultCode.NO_SUCH_OBJECT
    }

    if (result == ResultCode.SUCCESS) {
      Logger.info(s"${settings.encryption} LDAP connection to " + settings.host + ":" + settings.port + " established")
      val connectionPool = processor.map(
        new LDAPConnectionPool(connection, 1, 10, _)
      ).getOrElse(
        new LDAPConnectionPool(connection, 1, 10)
      )
      Some(connectionPool)
    } else {
      Logger.warn("Failed to establish connection to " + settings.host + ":" + settings.port)
      None
    }
  }

  /**
    * Creates a connection to an existing LDAP server instance.
    * @return LDAPConnection object
    */
  protected def createConnection: (LDAPConnection, Option[PostConnectProcessor]) = {
    val sslUtil: SSLUtil = setupSSLUtil

    settings.encryption match {
      case "ssl" =>
        // connect to server with ssl encryption
        val sslSocketFactory: SSLSocketFactory = sslUtil.createSSLSocketFactory()
        val connection = new LDAPConnection(sslSocketFactory, settings.host, settings.port)
        (connection, None)

      case "starttls" =>
        // connect to server with starttls connection
        val connection: LDAPConnection = new LDAPConnection(settings.host, settings.port)

        val sslContext: SSLContext = sslUtil.createSSLContext()
        connection.processExtendedOperation(new StartTLSExtendedRequest(sslContext))
        val processor = new StartTLSPostConnectProcessor(sslContext)
        (connection, Some(processor))

      case _ =>
        // create unsecured connection
        val connection = new LDAPConnection(settings.host, settings.port)
        (connection, None)
    }
  }

  /**
    * Setup SSL context (e.g for use with startTLS).
    * If a truststore file has been defined in the config, it will be loaded.
    * Otherwise, server certificates will be blindly trusted.
    * @return Created SSLContext.
    */
  protected def setupSSLUtil(): SSLUtil = {
    settings.trustStore match{
      case Some(path) => new SSLUtil(new TrustStoreTrustManager(path))
      case None => new SSLUtil(new TrustAllTrustManager())
    }
  }

  /**
    * Closes LDAPConnection or shuts down InMemoryDirectoryServer.
    * Ensures that application releases ports.
    * @param interface Interface to be disconnected or shut down.
    */
  private def terminateInterface(interface: Option[LDAPInterface]): Unit = {
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
    * Establish connection and check if bind possible.
    * Useful for authentification.
    * @param userDN DN for binding.
    * @param password password for binding.
    * @return true, if bind successful.
    */
  def canBind(userDN: String, password: String): Boolean = {
    logger.info(s"Checking the user credentials for $userDN in LDAP.")
    val (connection, _) = createConnection

    val result: ResultCode = try {
      connection.bind(userDN, password).getResultCode
    } catch {
      case _: LDAPException => ResultCode.NO_SUCH_OBJECT
    }

    // Close the connection
    connection.close()

    // Log the outcome
    val successfulWord = if (result == ResultCode.SUCCESS) "successful" else "unsuccessful"
    logger.info(s"The LDAP verification for $userDN is ${successfulWord.toUpperCase}.")

    result == ResultCode.SUCCESS
  }

  /**
    * Find Entry based on its DN.
    * @param dn Distinguished name for search operation.
    * @return Entry wrappend in Option if found; None else.
    */
  override def findByDN(dn: String): Option[Entry] =
    ldapinterface.flatMap( interface =>
      Option(interface.getEntry(dn))
    )

  /**
    * For debugging purposes.
    * Gets list of all entries.
    * @return List of ldap entries.
    */
  override def getEntryList: Traversable[String] =
    ldapinterface match {
      case Some(interface) => {
        val searchRequest: SearchRequest = new SearchRequest( settings.dit, SearchScope.SUB, Filter.create("(objectClass=*)"))
        val entries: Traversable[Entry] = dispatchSearchRequest(interface, searchRequest)
        entries.map(_.toString)
      }
      case None => Nil
    }

  /**
    * Dispatch SearchRequest and return list of found entries.
    * @param searchRequest Request to be dipatched.
    * @return List of matching entries.
    */
  override def dispatchRequest(searchRequest: SearchRequest): Traversable[Entry] =
    ldapinterface match {
      case Some(interface) => dispatchSearchRequest(interface, searchRequest)
      case None => Nil
    }

  /**
    * Secure, crash-safe ldap search method.
    *
    * @param interface interface to perform search request on.
    * @param request SearchRequest to be executed.
    * @return List of search results. Empty, if request failed.
    */
  private def dispatchSearchRequest(interface: LDAPInterface, request: SearchRequest): Traversable[Entry] =
    try {
      interface.search(request).getSearchEntries
    } catch {
      case e: Throwable => Nil
    }
}
