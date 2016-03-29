package ldap

import java.util

import com.unboundid.ldap.listener.{InMemoryDirectoryServerSnapshot, InMemoryDirectoryServer}
import com.unboundid.ldap.sdk._
import models.security.CustomUser


object LdapUtil {

  /**
    * TODO: add permissiosns and roles to users
    * Add users to ldap object
    * Make sure you called createTree, addRoles and addPermissions first.
    *
    * @param user CustomUser to add.
    */
  def userToEntry(user: CustomUser): Entry = {
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

    new Entry(dn, sn, cn, password, email, affiliation, objectClass, permissions, roles)
  }

  /**
    * Reconstruct CustomUser from ldap entry.
    * Use this convert SearchResultEntry or others to CustomUser.
    * If the entry does not point to a user, a CustomUser with null fields will be created.
    *
    * @param entry Entry as input for reconstruction.
    * @return CustomUser, if Entry is not null, None else.
    */
  def entryToUser(entry: Entry): Option[CustomUser] = {
    if (entry != null) {
      val name: String = entry.getAttributeValue("cn")
      val email: String = entry.getAttributeValue("mail")
      val password: String = entry.getAttributeValue("userPassword")
      val affiliation: String = entry.getAttributeValue("o")
      val permissions: Array[String] = entry.getAttributeValues("memberOf")
      val roles: Array[String] = entry.getAttributeValues("memberOf")
      Some(CustomUser(None, name, email, password, affiliation, permissions.toSeq, roles.toSeq))
    } else {
      None
    }
  }

  /**
    * For Debugging
    * Retrieves server entries and converts them to string.
    *
    * @return String representation of all server entries.
    */
  def getEntryList(interface: LDAPInterface, baseDN: String="dc=ncer"): List[String] = {
    val searchRequest: SearchRequest = new SearchRequest(baseDN, SearchScope.SUB, Filter.create("(objectClass=*)"))
    val searchResult: SearchResult = interface.search(searchRequest)
    val entries: util.List[SearchResultEntry] =  searchResult.getSearchEntries()
    var userStringList = List[String]()

    val it: util.Iterator[SearchResultEntry] = entries.iterator()
    while(it.hasNext){
      val entry: SearchResultEntry = it.next
      userStringList = entry.toString() :: userStringList
    }
    userStringList
  }

}
