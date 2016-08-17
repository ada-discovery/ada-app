package ldap

import dataaccess.User
import persistence.RepoTypes.UserRepo

import scala.collection.JavaConversions._

import com.unboundid.ldap.sdk._
import models.security.LdapUser
import models.workspace.UserGroup

import scala.concurrent.Future


object LdapUtil {
  // type definition for conversion methods
  type LdapConverter[T] = (Entry => Option[T])

  /**
    * Synchronize UserRepo with users from ldap server.
 *
    * @param connector
    * @param repo
    * @return
    */
  def synchronizeUserRepo(connector: LdapConnector, repo: UserRepo): Boolean = {
    val dit = connector.ldapsettings.dit
    val searchRequest: SearchRequest = new SearchRequest(dit, SearchScope.SUB, Filter.create("(objectClass=person)"))

    if(connector.ldapinterface.isDefined){
      val entries: Traversable[Entry] = connector.dispatchRequest(searchRequest)
      val users: Traversable[User] = convertAndFilter(entries, entryToUser)
      users.map{ usr: User => repo.update(usr) }
      true
    }else{
      false
    }
  }

  /**
    * TODO: add permissiosns and roles to users
    * Convert CustomUser class to Ldap entry.
    * Make sure the directory tree contains the necessary groups.
 *
    * @param user CustomUser to convert.
    * @return Converted user.
    */
  def userToEntry(user: User, dit: String = "dc=ncer"): Entry = {
    val dn = "dn: cn=" + user.email + ",dc=users," + dit
    val sn = "sn:" + user.ldapDn
    val cn = "cn:" + user.ldapDn
    val email = "mail:" + user.email
    val objectClass = "objectClass:person"
    //val affiliation = "o:" + user.affiliation

    if (!user.permissions.isEmpty) {
      val permissions: String = "memberOf:" + user.permissions.head
      new Entry(dn, sn, cn, email, objectClass, permissions)
    }else{
      new Entry(dn, sn, cn, email, objectClass)
    }
  }

  /**
    * Reconstruct CustomUser from ldap entry.
    * Use this convert SearchResultEntry or others to CustomUser.
    * If the entry does not point to a user, a CustomUser with null fields will be created.
 *
    * @param entry Entry as input for reconstruction.
    * @return CustomUser, if Entry is not null, None else.
    */
  def entryToUser(entry: Entry): Option[User] = {
    if(entry != null){
      val name: String = entry.getAttributeValue("uid")
      //val name: String = entry.getAttributeValue("cn")
      val email: String = entry.getAttributeValue("mail")
      val password: String = entry.getAttributeValue("userPassword")
      val affiliation: String = nullToDefault(entry.getAttributeValue("ou"), "")
      val roles: Array[String] = Array[String]()
      val permissions: Array[String] = nullToDefault(entry.getAttributeValues("memberof"), Array[String]())
      Some(User(None, name, email, roles, permissions))
    }else{
      None
    }
  }


  /**
    * Reconstruct CustomUser from ldap entry.
    * Use this convert SearchResultEntry or others to CustomUser.
    * If the entry does not point to a user, a CustomUser with null fields will be created.
 *
    * @param entry Entry as input for reconstruction.
    * @return CustomUser, if Entry is not null, None else.
    */
  def entryToLdapUser(entry: Entry): Option[LdapUser] =
    if(entry != null){
      val uid: String = entry.getAttributeValue("uid")
      val name: String = entry.getAttributeValue("cn")
      val email: String = entry.getAttributeValue("mail")
      val ou: String = entry.getAttributeValue("ou")
      val permissions: Array[String] = nullToDefault(entry.getAttributeValues("memberof"), Array[String]())
      Some(LdapUser(uid, name, email, ou, permissions))
    } else{
      None
    }

  /**
    * Convert entry to UserGroup object if possible.
 *
    * @param entry Ldap entry to be converted.
    * @return UserGroup, if conversion possible, None else.
    */
  def entryToUserGroup(entry: Entry): Option[UserGroup] = {
    if(entry != null){
      val name: String = entry.getAttributeValue("cn")
      val members: Array[String] = nullToDefault(entry.getAttributeValues("member"), Array())
      val description: Option[String] = asOption(entry.getAttributeValue("description"))
      val nested: Array[String] = nullToDefault(entry.getAttributeValues("memberof"), Array())
      Some(UserGroup(None, name, description, members.toSeq, nested.toSeq))
    }else{
      None
    }
  }

  /**
    * For Debugging
    * Retrieves server entries and converts them to string.
 *
    * @return String representation of all server entries.
    */
  def getEntryList(interface: LDAPInterface, baseDN: String = "dc=ncer"): Traversable[String] = {
    val searchRequest: SearchRequest = new SearchRequest(baseDN, SearchScope.SUB, Filter.create("(objectClass=*)"))
    val entries: Traversable[Entry] = dispatchSearchRequest(interface, searchRequest)
    convertAndFilter(entries, ((e) => Some(e.toString())))
  }

  /**
    * Secure, crash-safe ldap search method.
 *
    * @param interface interface to perform search request on.
    * @param request SearchRequest to be executed.
    * @return List of search results. Empty, if request failed.
    */
  def dispatchSearchRequest(interface: LDAPInterface, request: SearchRequest): Traversable[Entry] = {
    try {
      val result: SearchResult = interface.search(request)
      result.getSearchEntries
    }catch{case e: Throwable => Traversable[Entry]()}
  }

  /**
    * Convert entries with conversion function and filter out inconvertible results.
 *
    * @param entries List of entries to convert.
    * @param conv Conversion function. Should either return the converted value or None if conversion not possible.
    * @tparam T Type to convert Entries to.
    * @return Converted entries.
    */
  def convertAndFilter[T](entries: Traversable[Entry], conv: LdapConverter[T]): Traversable[T] = {
    val entryOps: Traversable[Option[T]] = entries.map(conv)
    entryOps.filter { entry => entry.isDefined }.map { e => e.get }
  }

  /**
    * Convert entries with conversion function and filter out inconvertible results.
 *
    * @param entries List of entries to convert.
    * @param conv Conversion function. Should either return the converted value or None if conversion not possible.
    * @tparam T Type to convert Entries to.
    * @return Converted entries.
    */
  def convertAndFilterImplicit[T](entries: Traversable[Entry]) (implicit conv: LdapConverter[T]): Traversable[T] = {
    val entryOps: Traversable[Option[T]] = entries.map(conv)
    entryOps.filter { entry => entry.isDefined }.map { e => e.get }
  }

  // helper: convert input value to default if null.
  def nullToDefault[T](value: T, default: T): T = {
    if (value == null)
      default
    else
      value
  }

  // helper convert value to option if defined, or convert to none if null given.
  def asOption[T](value: T): Option[T] = {
    if (value == null)
      None
    else
      Some(value)
  }

}
