package ldap

import java.util.Calendar

import com.google.inject.{Inject, ImplementedBy, Singleton}
import com.unboundid.ldap.sdk._
import models.workspace.UserGroup

import persistence.RepoTypes._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.FiniteDuration

import models.security.{UserManager, CustomUser}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._

import play.api.{Logger, Configuration}


// TODO deprecated
@ImplementedBy(classOf[AdaLdapUserServerImpl])
trait UserCache {
  // update interval in seconds
  protected val updateInterval: Int
  // time of last update
  protected var lastUpdate: Long = 0
  // current time in seconds
  protected def currentTime: Long = (Calendar.getInstance().getTimeInMillis() / 1000)
  // check if update required
  protected def needsUpdate: Boolean = ((currentTime - lastUpdate) > updateInterval)
  // lazy updating
  def updateCache(eager: Boolean = false): Boolean
}


/**
  * Create Ldap user server or use an established connection.
  * See options in ldap.conf for settings.
  * Users from the current user repo can be imported.
  */
@ImplementedBy(classOf[AdaLdapUserServerImpl])
trait AdaLdapUserServer extends UserManager{

  def addUsersFromRepo(interface: LDAPInterface, userRepo: UserRepo): Unit

  // debug
  def getEntryList: Traversable[String]

  def getUsers: Traversable[CustomUser]
  def getUserGroups: Traversable[UserGroup]
}


@Singleton
class AdaLdapUserServerImpl @Inject()(connector: LdapConnector, configuration: Configuration) extends AdaLdapUserServer with UserCache{

  val dit = configuration.getString("ldap.dit").getOrElse("dc=ncer")

  // list of groups to be used
  val groups: Seq[String] = configuration.getStringSeq("ldap.groups").getOrElse(Seq())

  // interval for lazy updates
  // use 0 for eager updates
  override val updateInterval: Int = configuration.getInt("ldap.updateinterval").getOrElse(1800)

  // switch for using debugusers "adminUser" and "basicUser"
  val addDebugUsers: Boolean = configuration.getBoolean("ldap.debugusers").getOrElse(false)

  var userCache: Traversable[CustomUser] = Traversable()
  var userGroupCache: Traversable[UserGroup] = Traversable()


  // TODO not implemented
  override def updateUser(user: CustomUser): Future[Boolean] = Future(true)

  /**
    * Return cached users.
    * @return
    */
  override def getUsers: Traversable[CustomUser] = {
    updateCache()
    userCache
  }

  /**
    * Return cached user groups.
    * @return
    */
  override def getUserGroups: Traversable[UserGroup] = {
    updateCache()
    userGroupCache
  }

  /**
    * Updates cached users and usergroups if necessary.
    * @param eager Set to true to enforce update; lazy updating is used otherwise.
    * @return true, if update successful or no update required.
    */
  override def updateCache(eager: Boolean): Boolean = {
    // build usergroup request
    val filterGroups = Filter.createEqualityFilter("objectClass", "groupofnames")
    val groupRequest: SearchRequest = new SearchRequest("cn=groups," + dit, SearchScope.SUB, filterGroups)

    // build user request
    val personFilter = Filter.createEqualityFilter("objectClass", "person")
    val filterUsers = if(!groups.isEmpty){
      val memberFilter: Seq[Filter] = groups.map{ groupname =>
        Filter.createEqualityFilter("memberof", groupname)
      }
      Filter.createANDFilter(Filter.createORFilter(memberFilter), personFilter)
    }else{
      personFilter
    }
    val userRequest: SearchRequest = new SearchRequest("cn=users," + dit, SearchScope.SUB, filterUsers)


    // update caches
    if(eager || needsUpdate){
      val resUsers: Traversable[Entry] = connector.dispatchRequest(userRequest)
      val convUsers: Traversable[CustomUser] = LdapUtil.convertAndFilter(resUsers, LdapUtil.entryToUser)
      val newUserCache = {
        if (addDebugUsers)
          convUsers ++ debugUsers
        else
          convUsers
      }

      val resGroups: Traversable[Entry] = connector.dispatchRequest(groupRequest)
      val convGroups: Traversable[UserGroup] = LdapUtil.convertAndFilter(resGroups, LdapUtil.entryToUserGroup)
      val newUserGroupCache = convGroups

      if(!(newUserCache.isEmpty || newUserGroupCache.isEmpty)){
        lastUpdate = currentTime
        userCache = newUserCache
        userGroupCache = newUserGroupCache
        if(!eager)
          Logger.info("ldap cache updated")
        true
      }else{
        Logger.warn("ldap cache update failed")
        false
      }
    }else{
       true
    }
  }


  /**
    * Authenticate by checking if mail exists, then trying bind operation.
    * @param id Mail for matching.
    * @param password Password which should match the password associated to the mail.
    * @return None, if password is wrong or not associated mail was found. Corresponding Account otherwise.
    */
  override def authenticate(id: String, password: String): Future[Boolean] = {
    findById(id).map{ usrOp:Option[CustomUser] => usrOp match{
        case Some(usr) =>
          if(debugUsers.find(u => u==usr).isDefined){
            true
          }else{
            val dn = "uid=" + usr.name + ",cn=users," + dit
            connector.canBind(dn, password)
          }
        case None =>
          false
      }
    }
  }

  /**
    * Unused.
    * Fetches users from database and inserts them into ldap object.
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
    * Unused.
    * Find specific DN as user.
    * @param dn String defining the full DN.
    * @return CustomUser wrapped in option. None, if user not found.
    */
  def findByDN(dn: String): Option[CustomUser] = {
    val entry: Option[Entry] = connector.findByDN(dn)
    if(entry.isDefined)
      LdapUtil.entryToUser(entry.get)
    else
      None
  }

  /**
    * Find first user matching specified uuid. Checks branch given by dit.
    * @param id uuid of user.
    * @return Future(None), if no user found; CustomUser wrappend in Option and Future else.
    */
  override def findById(id: String): Future[Option[CustomUser]] = {
    updateCache()
    Future(userCache.find((user: CustomUser) => user.getIdentifier == id))
  }

  /**
    * Find user with designated mail. Returns first valid match, if multiple ones exist.
    * Constructs CustomUser object from result.
    * @param email String of associated user mail.
    * @return CustomUser, if found, None else
    */
  override def findByEmail(email: String): Future[Option[CustomUser]] = {
    updateCache()
    Future(userCache.find((user: CustomUser) => user.email == email))
  }

  /**
    * For debugging purposes.
    * Gets list of all entries.
    * @return List of ldap entries.
    */
  override def getEntryList: Traversable[String] = {
    connector.getEntryList
  }
}
