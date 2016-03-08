package ldap

import java.util
import javax.inject.Inject

import _root_.util.SecurityUtil

import com.unboundid.ldap.listener.{InMemoryDirectoryServerSnapshot, InMemoryDirectoryServer, InMemoryDirectoryServerConfig, InMemoryListenerConfig}
import com.unboundid.ldap.sdk.{LDAPConnection, SearchResultEntry}
import com.unboundid.ldap.sdk._

import persistence.RepoTypes.UserRepo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import models.security.CustomUser
import models.security._


/**
  * Notes
  * Create InMemoryServer on startup and reuse in methods of application.
  *
  *
  */
class LdapUserManager @Inject()(userRepo: UserRepo) extends UserManager {

  // TODO: dummy user profiles
  override val adminUser = new CustomUser(None, "admin user", "admin@mail", SecurityUtil.md5("123456"), "None", List(SecurityRoleCache.adminRole), SecurityPermissionCache.adminPermissions)
  override val basicUser = new CustomUser(None, "basic user", "basic@mail", SecurityUtil.md5("123456"), "None", List(SecurityRoleCache.basicRole), SecurityPermissionCache.basicPermissions)

  // configuration
  val defaultPort: Int = 389
  val defaultHost: String = "localhost"                                       // url of ldap directory
  val serverTimeout: Int = 2000                                               // request timeout

  val defaultport: Int = 389                                                  // port of ldap directory
  val defaultbindDn: String = "admin@mail"                                    // overwrite with value from user repo; this is the username for binding
  val defaultPassword: String = "123456"                                      // overwrite with value from user repo; password associated to username

  val address: String = defaultHost                                           // address fro server connection

  val dit = "ncer"
  val ldapServer: InMemoryDirectoryServer = createServer


  /**
    * Create basic ldap tree structure with three branches: roles, permissions, users.
    * @param server
    */
  def createTree(server: InMemoryDirectoryServer): Unit = {
    // add root
    server.add("dn: dc=ncer", "objectClass: top", "objectClass: domain", "dc: ncer")
    // add subtrees: roles, permissions, people
    server.add("dn: dc=roles,dc=ncer", "objectClass: top", "objectClass: domain", "dc: roles")
    server.add("dn: dc=permissions,dc=ncer", "objectClass: top", "objectClass: domain", "dc: permissions")
    server.add("dn: dc=users,dc=ncer", "objectClass: top", "objectClass: domain", "dc: users")
  }

  /**
    * Add cached roles to InMemoryDirectoryServer and build flat permission tree.
    * @param server
    */
  def addPermissions(server: InMemoryDirectoryServer): Unit = {
    val dc : String = "dc=permissions,dc=ncer"
    val permissions: Seq[String] = SecurityPermissionCache.getPermissions
    //server.add("dn: dc=view,dc=permissions,dc=ncer", "objectClass: top", "objectClass: domain", "dc: view")
    //server.add("dn: dc=edit,dc=permissions,dc=ncer", "objectClass: top", "objectClass: domain", "dc: edit")
    //server.add("dn: dc=execute,dc=permissions,dc=ncer", "objectClass: top", "objectClass: domain", "dc: execute")
    permissions.foreach{p: String =>
      server.add("dn: dc=" + p + ","+dc, "objectClass: permission", "objectClass: top", "dc: "+p)
    }
  }

  /**
    * Add cached roles to InMemoryDirectoryServer and build flat role tree.
    * @param server
    */
  def addRoles(server: InMemoryDirectoryServer): Unit = {
    val dc : String = "dc=roles,dc=ncer"
    val roles: Seq[String] = SecurityRoleCache.getRoles
    roles.foreach{r: String =>
      val cn: String = "cn=" + r + ","
      server.add("dn: "+cn+dc, "objectClass: group", "objectClass: top", "dc: "+r)
    }
  }

  /**
    * TODO: add permissiosns and roles to users
    * Add users from database to InMemoryDirectoryServer.
    * Make sure you called createTree, addRoles and addPermissions first.
    * @param server
    */
  def addUsers(server: InMemoryDirectoryServer): Unit = {
    val timeout: FiniteDuration = 120000 millis
    val usersFuture: Future[Traversable[CustomUser]] = userRepo.find()
    val users: Traversable[CustomUser] = Await.result(usersFuture, timeout)

    users.foreach{user: CustomUser =>
      val dn = "dn: cn="+user.email+",dc=users,dc=ncer"
      val sn = "sn:" + user.name
      val cn = "cn:" + user.name
      val password = "userPassword:" + user.password
      val email = "mail:" + user.email
      val objectClass = "objectClass:person"
      val affiliation = "o:" + user.affiliation

      //user.permissions.fold("memberOf=")((a,b) => a+b)
      val permissions: String = if(user.permissions.isEmpty){"memberOf:none"}else{"memberOf:" + user.permissions.head}
      val roles: String = if (user.roles.isEmpty){"memberOf:none"}else{"memberOf:" + user.roles.head}

      server.add(dn, sn, cn, password, email, affiliation, objectClass, permissions, roles)
    }
  }

  /**
  * Creates an LDAP inmemory server for testing
  * Feed users from user database into server.
  *
  * @return dummy server
  */
  def createServer(): InMemoryDirectoryServer = {
    val config = new InMemoryDirectoryServerConfig("dc=ncer");
    val listenerConfig = new InMemoryListenerConfig("default", null, 12345, null, null, null);
    config.setListenerConfigs(listenerConfig);
    config.setSchema(null); // do not check (attribute) schema

    val server = new InMemoryDirectoryServer(config);
    server.startListening();

    // initialize ldap structures
    createTree(server)
    addPermissions(server)
    addRoles(server)
    // add users
    addUsers(server)

    server
  }

  /**
    * Untested
    * Clean and graceful server reset.
    * @param server
    */
  def resetServer(server: InMemoryDirectoryServer): Unit = {
    //server.closeAllConnections(true)
    server.clear()
    server.restartServer()
    server.restartListener("default")
  }


  /**
    * For Debugging
    * Retrieves snapshot of server entries and converts them to string.
    * @return String representation of all server entries.
    */
  def getEntryList: List[String] = {
    val server: InMemoryDirectoryServer = ldapServer
    val snapshot: InMemoryDirectoryServerSnapshot = server.createSnapshot
    val entryMap: util.Map[DN, ReadOnlyEntry] = snapshot.getEntryMap
    val entries: util.Collection[ReadOnlyEntry] = entryMap.values()
    var userStringList = List[String]()

    val it: util.Iterator[ReadOnlyEntry] = entries.iterator()
    while(it.hasNext){
    val entry: ReadOnlyEntry = it.next
      userStringList = entry.toString() :: userStringList
    }
    userStringList
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


  /**
    * Checks if user has given permission.
    * @param userid User name to be checked.
    * @param permission Permission to be checked.
    * @return true, if the user exists and has given permission associated.
    */
  def authorize(userid: String, permission: String): Boolean = {
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
    * TODO: change
    */
  override def findById(id: String): Future[Option[CustomUser]] = {
    findByEmail(id)
  }


  /**
    * TODO: exchange
    * Find user with designated mail in InMemoryServer.
    * Construct CustomUser object from result if possible.
    * @param email String of associated user mail
    * @return CustomUser, if found, None else
    */
  override def findByEmail(email: String): Future[Option[CustomUser]] = {
    val server = ldapServer
    val conn: LDAPConnection = server.getConnection()
    val entry: SearchResultEntry = conn.getEntry("cn="+email+",dc=users,dc=ncer")

    val user: Option[CustomUser] = if (entry != null) {
      val name: String = entry.getAttributeValue("cn")
      val password: String = entry.getAttributeValue("userPassword")
      val affiliation: String = entry.getAttributeValue("o")
      val permissions: Array[String] = entry.getAttributeValues("memberOf")
      val roles: Array[String] = entry.getAttributeValues("memberOf")
      Some(CustomUser(None, name, email, password, affiliation, permissions.toSeq, roles.toSeq))
    } else {
      None
    }
    Future(user)
  }

}
