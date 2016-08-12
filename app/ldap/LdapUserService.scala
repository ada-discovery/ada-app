package ldap

import com.google.inject.{Inject, Singleton, ImplementedBy}
import com.unboundid.ldap.sdk.{Entry, SearchRequest, Filter, SearchScope}
import models.security.LdapUser
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
    LdapUtil.convertAndFilter(entries, LdapUtil.entryToLdapUser)
  }
}
