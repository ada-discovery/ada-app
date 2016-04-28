package ldap

import com.unboundid.ldap.sdk._
import ldap.LdapUtil.LdapConverter

import persistence.{Sort, AsyncReadonlyRepo}
import play.api.libs.json.JsObject

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  *
  */
class LdapRepo[T <: LdapDN](dit: String, converter: LdapConverter[T], connector: LdapConnector) extends AsyncReadonlyRepo[T, String] with ObjectCache[T]{

  // TODO: user config
  override val updateInterval: Int = 1800//configuration.getInt("ldap.updateinterval").getOrElse(1800)
  override val updateCall: (() => Traversable[T]) = requestList

  def get(id: String): Future[Option[T]] = {
    val res: Option[T] = getCache(false).find{entry => (entry.getDN == id)}
    Future(res)
  }

  // TODO argument and criteria conversion
  // no filtering yet
  def find(
    criteria: Option[JsObject] = None,
    orderBy: Option[Seq[Sort]] = None,
    projection : Option[JsObject] = None,
    limit: Option[Int] = None,
    page: Option[Int] = None
  ): Future[Traversable[T]] = {
    Future(getCache(false))
  }


  def count(criteria: Option[JsObject] = None): Future[Int] = {
    find(criteria).map( (found: Traversable[T]) => found.size)
  }

  // override for more specific behaviour
  def requestList(): Traversable[T] = {
    val baseDN = dit
    val scope = SearchScope.SUB
    val filter = Filter.create("(objectClass=*)")
    val request: SearchRequest = new SearchRequest(baseDN, scope, filter)

    val entries: Traversable[Entry] = connector.dispatchRequest(request)
    LdapUtil.convertAndFilter(entries, converter)
  }
}



