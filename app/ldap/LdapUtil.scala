package ldap

import java.util

import com.unboundid.ldap.listener.{InMemoryDirectoryServerSnapshot, InMemoryDirectoryServer}
import com.unboundid.ldap.sdk._
import com.unboundid.ldap.sdk.LDAPSearchException

import models.security.CustomUser


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
    if (entry != null) {
      //val name: String = entry.getAttributeValue("uid")
      val name: String = entry.getAttributeValue("cn")
      val email: String = entry.getAttributeValue("mail")
      val password: String = entry.getAttributeValue("userPassword")
      val affiliation: String = entry.getAttributeValue("ou")
      val permissions: Array[String] = entry.getAttributeValues("memberof")
      //val roles: Array[String] = entry.getAttributeValues("memberof")
      val roles: Array[String] = Array()
      Some(CustomUser(None, name, email, password, affiliation, permissions.toSeq, roles.toSeq))
    } else {
      None
    }
  }


  //def entryToJSON(entry: Entry): JsonEntry = {}

  /**
    * For Debugging
    * Retrieves server entries and converts them to string.
    * @return String representation of all server entries.
    */
  def getEntryList(interface: LDAPInterface, baseDN: String="dc=ncer"): List[String] = {
    val searchRequest: SearchRequest = new SearchRequest(baseDN, SearchScope.SUB, Filter.create("(objectClass=*)"))
    var userStringList = List[String]()
    try{
      val searchResult: SearchResult = interface.search(searchRequest)
      val entries: util.List[SearchResultEntry] =  searchResult.getSearchEntries()
      val it: util.Iterator[SearchResultEntry] = entries.iterator()
      while(it.hasNext){
        val entry: SearchResultEntry = it.next
        userStringList = entry.toString() :: userStringList
      }
    }catch{case _ => Unit}
    userStringList
  }

  // helper: convert value to default if null.
  def nullToDefault[T](value: T, default: T): T = {
    if(value == null)
      default
    else
      value
  }

  // helper convert value to option if defined, or convert to none if null given.
  def asOption[T](value: T): Option[T] = {
    if(value == null)
      None
    else
      Some(value)
  }
}
