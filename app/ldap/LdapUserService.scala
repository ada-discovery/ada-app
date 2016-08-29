package ldap

import com.google.inject.{Inject, Singleton, ImplementedBy}
import com.unboundid.ldap.sdk.{Entry, SearchRequest, Filter, SearchScope}
import models.security.LdapUser
import models.workspace.UserGroup
import scala.collection.JavaConversions._

import play.api.libs.json.Json

@ImplementedBy(classOf[LdapUserServiceImpl])
trait LdapUserService {
  def getAll: Traversable[LdapUser]
}

protected class LdapUserServiceImpl @Inject()(connector: LdapConnector) extends LdapUserService{

  private val settings = connector.ldapsettings
  private val groups: Seq[String] = settings.groups
  private val dit: String = settings.dit

  override def getAll: Traversable[LdapUser] = {
    val baseDN = "cn=users," + dit
    val personFilter = Filter.createEqualityFilter("objectClass", "person")
    val filterUsers = if(!groups.isEmpty){
      val memberFilter: Seq[Filter] = groups.map{ groupname =>
        Filter.createEqualityFilter("memberof", groupname)
      }
      Filter.createANDFilter(Filter.createORFilter(memberFilter), personFilter)
    }else{
      personFilter
    }

    val request: SearchRequest = new SearchRequest(baseDN, SearchScope.SUB, filterUsers)
    val entries: Traversable[Entry] = connector.dispatchRequest(request)
    entries.map(entryToLdapUser).flatten
  }

  /**
    * Reconstruct CustomUser from ldap entry.
    * Use this convert SearchResultEntry or others to CustomUser.
    * If the entry does not point to a user, a CustomUser with null fields will be created.
    *
    * @param entry Entry as input for reconstruction.
    * @return CustomUser, if Entry is not null, None else.
    */
  private def entryToLdapUser(entry: Entry): Option[LdapUser] =
    Option(entry).map{ entry =>
      val uid: String = entry.getAttributeValue("uid")
      val name: String = entry.getAttributeValue("cn")
      val email: String = entry.getAttributeValue("mail")
      val ou: String = entry.getAttributeValue("ou")
      val permissions: Array[String] = Option(entry.getAttributeValues("memberof")).getOrElse(Array())
      LdapUser(uid, name, email, ou, permissions)
    }

  /**
    * Convert entry to UserGroup object if possible.
    *
    * @param entry Ldap entry to be converted.
    * @return UserGroup, if conversion possible, None else.
    */
  private def entryToUserGroup(entry: Entry): Option[UserGroup] =
    Option(entry) match {
      case None => None
      case _ => {
        val name: String = entry.getAttributeValue("cn")
        val members: Array[String] = Option(entry.getAttributeValues("member")).getOrElse(Array())
        val description: Option[String] = Option(entry.getAttributeValue("description"))
        val nested: Array[String] = Option(entry.getAttributeValues("memberof")).getOrElse(Array())
        Some(UserGroup(None, name, description, members, nested))
      }
    }
}