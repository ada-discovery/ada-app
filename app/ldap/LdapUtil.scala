package ldap

import java.util

import scala.collection.JavaConversions._

import com.unboundid.ldap.sdk._

import models.security.CustomUser
import models.workspace.UserGroup



object LdapUtil {

  /**
    * TODO: add permissiosns and roles to users
    * Add users to ldap object
    * Make sure you called createTree, addRoles and addPermissions first.
    * @param user CustomUser to add.
    */
  def userToEntry(user: CustomUser, dit: String = "dc=ncer"): Entry = {
    val dn = "dn: cn=" + user.email + ",dc=users," + dit
    val sn = "sn:" + user.name
    val cn = "cn:" + user.name
    val password = "userPassword:" + user.password
    val email = "mail:" + user.email
    val objectClass = "objectClass:person"
    val affiliation = "o:" + user.affiliation

    //user.permissions.fold("memberOf=")((a,b) => a+b)
    val permissions: String = if (user.permissions.isEmpty) {
      "memberOf:none"
    } else {
      "memberOf:" + user.permissions.head
    }
    val roles: String = if (user.roles.isEmpty) {
      "memberOf:none"
    } else {
      "memberOf:" + user.roles.head
    }
    new Entry(dn, sn, cn, password, email, affiliation, objectClass, permissions, roles)
  }

  /**
    * Reconstruct CustomUser from ldap entry.
    * Use this convert SearchResultEntry or others to CustomUser.
    * If the entry does not point to a user, a CustomUser with null fields will be created.
    * @param entry Entry as input for reconstruction.
    * @return CustomUser, if Entry is not null, None else.
    */
  def entryToUser(entry: Entry): Option[CustomUser] = {
    if(entry != null){
      val name: String = entry.getAttributeValue("uid")
      //val name: String = entry.getAttributeValue("cn")
      val email: String = entry.getAttributeValue("mail")
      val password: String = entry.getAttributeValue("userPassword")
      val affiliation: String = nullToDefault(entry.getAttributeValue("ou"), "")
      val permissions: Array[String] = nullToDefault(entry.getAttributeValues("memberof"), Array())
      val roles: Array[String] = Array()
      Some(CustomUser(None, name, email, "", affiliation, permissions.toSeq, roles.toSeq))
    }else{
      None
    }
  }


  /**
    * Convert entry to UserGroup object if possible
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
    * @return String representation of all server entries.
    */
  def getEntryList(interface: LDAPInterface, baseDN: String = "dc=ncer"): List[String] = {
    val searchRequest: SearchRequest = new SearchRequest(baseDN, SearchScope.SUB, Filter.create("(objectClass=*)"))
    var userStringList = List[String]()
    try {
      val searchResult: SearchResult = interface.search(searchRequest)
      val entries: util.List[SearchResultEntry] = searchResult.getSearchEntries()
      val it: util.Iterator[SearchResultEntry] = entries.iterator()
      while (it.hasNext) {
        val entry: SearchResultEntry = it.next
        userStringList = entry.toString() :: userStringList
      }
    } catch {
      case _: Throwable => Unit
    }
    userStringList
  }


  /**
    * Secure, crash-safe ldap search method.
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
    * Convert entries with conversion function and filter out unconvertable results.
    * @param entries List of entries to convert.
    * @param conv Conversion function. Should either return the converted value or None if conversion not possible.
    * @tparam T Type to convert Entries to.
    * @return Converted entries.
    */
  def convertAndFilter[T](entries: Traversable[Entry], conv: (Entry => Option[T])): Traversable[T] = {
    val entryOps: Traversable[Option[T]] = entries.map {
      conv
    }
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
